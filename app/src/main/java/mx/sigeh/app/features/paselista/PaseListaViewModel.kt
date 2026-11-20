package mx.sigeh.app.features.paselista

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.ServicioActivo
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.GeneradorIds
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.designsystem.components.AcuseSnackbarVisuals
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.sync.ConnectivityObserver

/** Lo que el ViewModel sabe; [PaseListaUiState] se deriva de aquí. */
private data class Datos(
    val servicio: ServicioActivo?,
    val zona: String,
    val fecha: String,
    /** La fecha sigue a «hoy»: si cambia la zona del hospital, se recalcula. */
    val sigueHoy: Boolean = true,
    val horarioId: String? = null,
    val turnos: List<ChipTurno> = emptyList(),
    val jornadas: List<JornadaPase> = emptyList(),
    val siguienteCursor: String? = null,
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val cargandoMas: Boolean = false,
    val errorCarga: Boolean = false,
    val bloqueo: BloqueoPase? = null,
    /** Jornadas con un `PUT` en curso: sus botones se desactivan (sin doble toque). */
    val envios: Set<String> = emptySet(),
    val hoja: Pair<String, EstadoCaptura>? = null,
    val dialogo: DialogoCaptura? = null,
    val resaltada: String? = null,
)

/**
 * Pantallas 9.3 a 9.6 · Pase de lista en línea (CU-ASI-01/02).
 *
 * Reglas del prompt M2 §5 que se cumplen aquí:
 * - Solo en línea; nada se marca por defecto; el servidor decide la capturabilidad.
 * - La `Idempotency-Key` se genera **una vez** al confirmar, ligada a `(programacionId, estado)`, y se
 *   guarda en [SavedStateHandle] hasta una respuesta terminal. Se reutiliza en reintentos
 *   (timeout, red, 503, 401) y se reemplaza si el estado cambia.
 * - Un envío en curso desactiva la jornada: dos toques nunca producen dos peticiones.
 * - La confirmación se muestra solo tras el 200 y después se vuelve a pedir `GET /pases-lista`.
 * - La fecha es la local del hospital: se empieza con `America/Mexico_City` y se adopta la
 *   `zonaHoraria` que envía el servidor.
 */
@HiltViewModel
class PaseListaViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val scopeCache: ScopeCache,
    private val repositorio: PaseListaRepository,
    private val sessionStore: SessionStore,
    private val conectividad: ConnectivityObserver,
    private val reloj: Reloj,
    private val ids: GeneradorIds,
) : ViewModel() {

    private val datos: MutableStateFlow<Datos>

    private val _eventos = Channel<PaseListaEvento>(Channel.BUFFERED)
    val eventos: Flow<PaseListaEvento> = _eventos.receiveAsFlow()

    private var consulta: Job? = null
    private var resaltado: Job? = null

    init {
        val zona = savedState.get<String>(CLAVE_ZONA) ?: HoraHospital.ZONA_POR_DEFECTO
        val fecha = savedState.get<String>(CLAVE_FECHA) ?: HoraHospital.hoy(reloj.ahoraMillis(), zona)
        datos = MutableStateFlow(
            Datos(
                servicio = scopeCache.servicioActual(),
                zona = zona,
                fecha = fecha,
                sigueHoy = fecha == HoraHospital.hoy(reloj.ahoraMillis(), zona),
                cargando = scopeCache.servicioActual() != null,
            ),
        )
        if (datos.value.servicio != null) consultar(primeraVez = true)
    }

    val uiState: StateFlow<PaseListaUiState> =
        combine(datos, conectividad.enLinea, sessionStore.session) { d, enLinea, sesion -> construir(d, enLinea, sesion?.usuarioId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), construir(datos.value, conectividad.enLinea.value, null))

    // --- Consulta ------------------------------------------------------------------------------

    /** «Actualizar», pull to refresh y regreso a primer plano: la capturabilidad cambia con la hora del servidor. */
    fun onActualizar() = consultar(refrescando = true)

    fun onReanudar() {
        if (!datos.value.cargando) consultar(refrescando = true)
    }

    fun onFechaSeleccionada(fecha: String) {
        val hoy = HoraHospital.hoy(reloj.ahoraMillis(), datos.value.zona)
        datos.update { it.copy(fecha = fecha, sigueHoy = fecha == hoy, horarioId = null, turnos = emptyList()) }
        savedState[CLAVE_FECHA] = fecha
        consultar()
    }

    fun onDiaAnterior() = onFechaSeleccionada(HoraHospital.sumarDias(datos.value.fecha, -1))

    fun onDiaSiguiente() = onFechaSeleccionada(HoraHospital.sumarDias(datos.value.fecha, 1))

    fun onHoy() = onFechaSeleccionada(HoraHospital.hoy(reloj.ahoraMillis(), datos.value.zona))

    /** Chip de turno: con horario se vuelve a consultar con `horarioId`; «Todos» es null. */
    fun onTurnoSeleccionado(horarioId: String?) {
        if (datos.value.horarioId == horarioId) return
        datos.update { it.copy(horarioId = horarioId) }
        consultar()
    }

    /** «Cargar más» con los mismos filtros. */
    fun onCargarMas() {
        val d = datos.value
        val cursor = d.siguienteCursor ?: return
        val servicio = d.servicio ?: return
        if (d.cargandoMas || consulta?.isActive == true) return
        datos.update { it.copy(cargandoMas = true) }
        consulta = viewModelScope.launch {
            when (val r = repositorio.consultar(servicio.hospitalId, servicio.servicioId, d.fecha, d.horarioId, cursor)) {
                is ResultadoConsulta.Exito -> datos.update {
                    it.copy(
                        jornadas = (it.jornadas + r.pagina.jornadas).distinctBy { j -> j.programacionId },
                        siguienteCursor = r.pagina.siguienteCursor,
                        cargandoMas = false,
                    )
                }
                else -> aplicarFallo(r)
            }
        }
    }

    private fun consultar(refrescando: Boolean = false, primeraVez: Boolean = false) {
        val servicio = datos.value.servicio ?: return
        consulta?.cancel()
        datos.update {
            it.copy(
                cargando = primeraVez || it.jornadas.isEmpty(),
                refrescando = refrescando && it.jornadas.isNotEmpty(),
                cargandoMas = false,
                errorCarga = false,
            )
        }
        consulta = viewModelScope.launch {
            var d = datos.value
            var resultado = repositorio.consultar(servicio.hospitalId, servicio.servicioId, d.fecha, d.horarioId)
            // La primera consulta usa la zona por defecto; si el hospital está en otra y la fecha
            // seguía a «hoy», se recalcula «hoy» en esa zona y se repite una vez.
            if (resultado is ResultadoConsulta.Exito && resultado.pagina.zonaHoraria != d.zona) {
                val zona = HoraHospital.zona(resultado.pagina.zonaHoraria).id
                savedState[CLAVE_ZONA] = zona
                val hoy = HoraHospital.hoy(reloj.ahoraMillis(), zona)
                datos.update { it.copy(zona = zona, fecha = if (it.sigueHoy) hoy else it.fecha) }
                if (d.sigueHoy && hoy != d.fecha) {
                    savedState[CLAVE_FECHA] = hoy
                    d = datos.value
                    resultado = repositorio.consultar(servicio.hospitalId, servicio.servicioId, d.fecha, d.horarioId)
                }
            }
            when (resultado) {
                is ResultadoConsulta.Exito -> {
                    val pagina = resultado.pagina
                    descartarIntencionesResueltas(pagina.jornadas)
                    datos.update {
                        it.copy(
                            jornadas = pagina.jornadas,
                            siguienteCursor = pagina.siguienteCursor,
                            // Los chips se derivan de la vista «Todos» y se conservan al filtrar.
                            turnos = if (it.horarioId == null) turnosDe(pagina.jornadas) else it.turnos,
                            cargando = false,
                            refrescando = false,
                            errorCarga = false,
                            bloqueo = null,
                        )
                    }
                }
                else -> aplicarFallo(resultado)
            }
        }
    }

    private suspend fun aplicarFallo(resultado: ResultadoConsulta) {
        val bloqueo = when (resultado) {
            ResultadoConsulta.SinPermiso -> BloqueoPase.SIN_PERMISO
            ResultadoConsulta.ContextoNoDisponible -> BloqueoPase.CONTEXTO_NO_DISPONIBLE
            ResultadoConsulta.NoHabilitada -> BloqueoPase.NO_HABILITADO
            ResultadoConsulta.SesionTerminada -> BloqueoPase.SESION_TERMINADA
            ResultadoConsulta.Fallida, is ResultadoConsulta.Exito -> null
        }
        if (bloqueo == BloqueoPase.SESION_TERMINADA) sessionStore.marcarVencida()
        datos.update {
            it.copy(cargando = false, refrescando = false, cargandoMas = false, bloqueo = bloqueo, errorCarga = bloqueo == null)
        }
    }

    // --- Captura -------------------------------------------------------------------------------

    /** Presente, Ausente o Parcial en una fila: abre la confirmación con ese estado. Nada se envía aún. */
    fun onAbrirCaptura(programacionId: String, estado: EstadoCaptura) {
        val d = datos.value
        val jornada = d.jornadas.firstOrNull { it.programacionId == programacionId } ?: return
        if (!jornada.capturable || programacionId in d.envios || !conectividad.enLinea.value) return
        datos.update { it.copy(hoja = programacionId to estado) }
    }

    fun onEstadoHoja(estado: EstadoCaptura) {
        val hoja = datos.value.hoja ?: return
        if (hoja.first in datos.value.envios) return
        datos.update { it.copy(hoja = hoja.first to estado) }
    }

    /** «Cancelar»: no envía nada. */
    fun onCancelarHoja() {
        val hoja = datos.value.hoja ?: return
        if (hoja.first in datos.value.envios) return
        datos.update { it.copy(hoja = null) }
    }

    /** «Registrar {estado}». */
    fun onConfirmar(estado: EstadoCaptura) {
        val programacionId = datos.value.hoja?.first ?: return
        enviar(programacionId, estado)
    }

    /** «Reintentar» del diálogo incierto: misma jornada, mismo estado y, por tanto, misma clave. */
    fun onReintentar() {
        val dialogo = datos.value.dialogo as? DialogoCaptura.Incierta ?: return
        datos.update { it.copy(dialogo = null) }
        enviar(dialogo.programacionId, dialogo.estado)
    }

    /** «Más tarde»: la clave se queda en [SavedStateHandle] para el próximo intento con el mismo estado. */
    fun onCerrarDialogo() {
        val eraIncierta = datos.value.dialogo is DialogoCaptura.Incierta
        datos.update { it.copy(dialogo = null) }
        if (eraIncierta) consultar(refrescando = true)
    }

    private fun enviar(programacionId: String, estado: EstadoCaptura) {
        val d = datos.value
        // Doble toque o sin red: no se produce una segunda petición.
        if (programacionId in d.envios || !conectividad.enLinea.value) return
        val clave = claveParaIntencion(programacionId, estado)
        datos.update {
            // Solo la hoja de esta jornada refleja el estado que se envía; otra hoja abierta no se toca.
            it.copy(envios = it.envios + programacionId, hoja = it.hoja?.let { h -> if (h.first == programacionId) h.copy(second = estado) else h })
        }
        viewModelScope.launch {
            val resultado = repositorio.capturar(programacionId, estado, clave)
            if (resultado.esTerminal) descartarIntencion(programacionId)
            datos.update { it.copy(envios = it.envios - programacionId) }
            manejar(resultado, programacionId, estado)
        }
    }

    private suspend fun manejar(resultado: ResultadoCaptura, programacionId: String, estado: EstadoCaptura) {
        val cerrarHoja: (Datos) -> Datos = { it.copy(hoja = it.hoja?.takeUnless { h -> h.first == programacionId }) }
        when (resultado) {
            is ResultadoCaptura.Confirmada -> {
                val zona = datos.value.zona
                val nombre = nombreDe(programacionId)
                datos.update { cerrarHoja(it).copy(resaltada = programacionId) }
                _eventos.send(
                    PaseListaEvento.MostrarAcuse(
                        AcuseSnackbarVisuals(nombre, resultado.acuse.estado, HoraHospital.horaConSegundos(resultado.acuse.registradoEn, zona)),
                        programacionId,
                    ),
                )
                quitarResaltadoDespues()
                consultar(refrescando = true)
            }
            is ResultadoCaptura.Rechazada -> {
                val dialogo = when (resultado.motivo) {
                    MotivoRechazo.YA_REGISTRADA -> DialogoCaptura.YaRegistrada(programacionId)
                    MotivoRechazo.FUERA_VENTANA -> DialogoCaptura.FueraDeHorario
                    MotivoRechazo.CONFLICTO_IDEMPOTENCIA, MotivoRechazo.SOLICITUD_INVALIDA -> DialogoCaptura.ErrorInterno
                    MotivoRechazo.PROGRAMACION_NO_CAPTURABLE, MotivoRechazo.CONTRATO_NO_VIGENTE,
                    MotivoRechazo.CIERRE_EXISTENTE, MotivoRechazo.NO_ADMITE_CAPTURA,
                    -> DialogoCaptura.NoAdmiteCaptura(textoRechazo(resultado.motivo))
                }
                datos.update { cerrarHoja(it).copy(dialogo = dialogo) }
                consultar(refrescando = true)
            }
            ResultadoCaptura.SinPermiso -> datos.update { cerrarHoja(it).copy(bloqueo = BloqueoPase.SIN_PERMISO) }
            ResultadoCaptura.NoEncontrada -> {
                datos.update { cerrarHoja(it).copy(dialogo = DialogoCaptura.NoDisponible) }
                consultar(refrescando = true)
            }
            ResultadoCaptura.Incierta -> datos.update { cerrarHoja(it).copy(dialogo = DialogoCaptura.Incierta(programacionId, estado)) }
            ResultadoCaptura.NoHabilitada -> datos.update { cerrarHoja(it).copy(dialogo = DialogoCaptura.NoHabilitada) }
            ResultadoCaptura.SesionTerminada -> {
                datos.update { cerrarHoja(it).copy(bloqueo = BloqueoPase.SESION_TERMINADA) }
                sessionStore.marcarVencida()
            }
        }
    }

    private fun quitarResaltadoDespues() {
        resaltado?.cancel()
        resaltado = viewModelScope.launch {
            delay(RESALTADO_MS)
            datos.update { it.copy(resaltada = null) }
        }
    }

    private fun nombreDe(programacionId: String): String =
        datos.value.jornadas.firstOrNull { it.programacionId == programacionId }?.nombre
            ?: repositorio.jornada(programacionId)?.nombre.orEmpty()

    // --- Idempotency-Key en SavedStateHandle --------------------------------------------------

    private fun intenciones(): List<IntencionPendiente> =
        savedState.get<String>(CLAVE_INTENCIONES)
            ?.let { runCatching { SigehJson.decodeFromString(ListSerializer(IntencionPendiente.serializer()), it) }.getOrNull() }
            .orEmpty()

    private fun guardarIntenciones(lista: List<IntencionPendiente>) {
        savedState[CLAVE_INTENCIONES] = SigehJson.encodeToString(ListSerializer(IntencionPendiente.serializer()), lista)
    }

    /** La misma clave para la misma `(jornada, estado)`; otro estado es otra intención y lleva clave nueva. */
    private fun claveParaIntencion(programacionId: String, estado: EstadoCaptura): String {
        val actuales = intenciones()
        actuales.firstOrNull { it.programacionId == programacionId && it.estado == estado }?.let { return it.clave }
        val nueva = IntencionPendiente(programacionId, estado, ids.nuevoId())
        guardarIntenciones(actuales.filterNot { it.programacionId == programacionId } + nueva)
        return nueva.clave
    }

    private fun descartarIntencion(programacionId: String) =
        guardarIntenciones(intenciones().filterNot { it.programacionId == programacionId })

    /** Si el servidor ya muestra capturada una jornada con intención pendiente, esa intención quedó resuelta. */
    private fun descartarIntencionesResueltas(jornadas: List<JornadaPase>) {
        val capturadas = jornadas.filter { it.capturada }.map { it.programacionId }.toSet()
        val actuales = intenciones()
        if (actuales.any { it.programacionId in capturadas }) guardarIntenciones(actuales.filterNot { it.programacionId in capturadas })
    }

    // --- Estado de UI ----------------------------------------------------------------------------

    private fun construir(d: Datos, enLinea: Boolean, usuarioId: String?): PaseListaUiState {
        val servicio = d.servicio
        val ahora = reloj.ahoraMillis()
        val filas = d.jornadas.map { j ->
            val tipo = when {
                j.programacionId in d.envios -> TipoFila.ENVIANDO
                j.capturada -> TipoFila.CAPTURADA
                j.capturable -> TipoFila.CAPTURABLE
                else -> TipoFila.NO_CAPTURABLE
            }
            FilaJornada(
                programacionId = j.programacionId,
                nombre = j.nombre,
                detalle = detalleJornada(j, d.zona),
                tipo = tipo,
                estadoOperativo = j.estadoOperativo,
                estadoProgramacion = j.estadoProgramacion,
                lineaCaptura = lineaCaptura(j, usuarioId, d.zona),
                motivo = textoMotivo(j, d.zona, ahora),
                resaltada = j.programacionId == d.resaltada,
            )
        }
        // «En esta página»: m cuenta los items capturables o capturados.
        val considerados = d.jornadas.filter { it.capturable || it.capturada }
        val hoja = d.hoja?.let { (id, estado) ->
            d.jornadas.firstOrNull { it.programacionId == id }?.let { j ->
                HojaCaptura(
                    programacionId = id,
                    nombre = j.nombre,
                    detalle = listOfNotNull(detalleJornada(j, d.zona), servicio?.servicioNombre).joinToString(" · "),
                    estado = estado,
                    enviando = id in d.envios,
                )
            }
        }
        return PaseListaUiState(
            cargando = d.cargando,
            refrescando = d.refrescando,
            cargandoMas = d.cargandoMas,
            servicio = servicio?.servicioNombre.orEmpty(),
            subtitulo = listOfNotNull(servicio?.hospitalNombre, d.zona).joinToString(" · "),
            fecha = d.fecha,
            fechaTexto = HoraHospital.fechaLarga(d.fecha),
            esHoy = d.fecha == HoraHospital.hoy(ahora, d.zona),
            turnos = d.turnos,
            horarioSeleccionado = d.horarioId,
            filas = filas,
            capturadas = considerados.count { it.capturada },
            capturables = considerados.size,
            conteos = considerados.groupingBy { it.estadoOperativo }.eachCount()
                .filterKeys { it != EstadoAsistencia.NO_EXIGIBLE },
            hayMas = d.siguienteCursor != null,
            enLinea = enLinea,
            errorCarga = d.errorCarga,
            bloqueo = d.bloqueo,
            hoja = hoja,
            dialogo = d.dialogo,
            sinServicio = servicio == null,
        )
    }

    companion object {
        internal const val CLAVE_INTENCIONES = "pase_intenciones"
        private const val CLAVE_FECHA = "pase_fecha"
        private const val CLAVE_ZONA = "pase_zona"
        private const val RESALTADO_MS = 4_000L

        fun textoRechazo(motivo: MotivoRechazo): String = when (motivo) {
            MotivoRechazo.PROGRAMACION_NO_CAPTURABLE -> "La programación de esta jornada ya no admite captura."
            MotivoRechazo.CONTRATO_NO_VIGENTE -> "El contrato de la persona no está vigente."
            MotivoRechazo.CIERRE_EXISTENTE -> "El balance del periodo ya está cerrado."
            else -> "El servidor no admite captura para esta jornada."
        }
    }
}
