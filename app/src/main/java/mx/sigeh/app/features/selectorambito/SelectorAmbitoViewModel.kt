package mx.sigeh.app.features.selectorambito

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.AmbitoAutorizado
import mx.sigeh.app.core.auth.ContextoPropio
import mx.sigeh.app.core.auth.PoliticaAmbito
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.ServicioActivo
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.AlcanceRepository
import mx.sigeh.app.core.data.ContextosRepository
import mx.sigeh.app.core.data.EtiquetasContexto
import mx.sigeh.app.core.data.EtiquetasPorIdentificador
import mx.sigeh.app.core.data.ResultadoContextos
import mx.sigeh.app.core.model.AmbitoActivo

/** Ámbito con sus nombres ya resueltos. */
private data class AmbitoConNombres(
    val ambito: AmbitoAutorizado,
    val hospital: String,
    val division: String?,
    val servicio: String?,
) {
    val clave: String get() = listOf(ambito.hospitalId, ambito.divisionId, ambito.servicioId).joinToString("|")
}

/**
 * Pantalla 9.2 · Elige tu servicio. Las opciones salen **solo** de lo que el servidor informa para
 * la sesión, mediante [PoliticaAmbito]; no se llama a `/servicios`:
 * 1. `GET /auth/me/contextos`: contextos propios vigentes con nombres, zona del hospital y vigencia.
 * 2. Si contextos no está disponible (503 mientras espera validación SQL, otro error o sin red):
 *    las asignaciones de `/auth/me` guardadas en la sesión, con etiquetas por identificador.
 * Un 401 en contextos (tras la única renovación) marca la sesión vencida y la navegación vuelve al login.
 */
@HiltViewModel
class SelectorAmbitoViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val scopeCache: ScopeCache,
    private val contextos: ContextosRepository,
    private val etiquetas: EtiquetasContexto,
    private val sesionManager: SesionManager,
    private val alcanceRepository: AlcanceRepository,
    private val interruptores: Interruptores,
) : ViewModel() {

    private val ambitos = MutableStateFlow<List<AmbitoConNombres>>(emptyList())
    private val _uiState = MutableStateFlow(SelectorAmbitoUiState())
    val uiState: StateFlow<SelectorAmbitoUiState> = _uiState.asStateFlow()

    private val _eventos = Channel<SelectorAmbitoEvento>(Channel.BUFFERED)
    val eventos: Flow<SelectorAmbitoEvento> = _eventos.receiveAsFlow()

    private var seleccionada: String? = null

    init {
        viewModelScope.launch {
            val lista = when (val resultado = contextos.contextosPropios()) {
                is ResultadoContextos.Exito -> deContextos(resultado.contextos)
                ResultadoContextos.SesionVencida -> {
                    sessionStore.marcarVencida()
                    emptyList()
                }
                is ResultadoContextos.NoDisponible, ResultadoContextos.SinConexion -> deAsignaciones()
            }
            ambitos.value = lista
            val habilitadas = lista.filter { it.ambito.habilitado }
            val previa = scopeCache.servicioActual()?.servicioId
            seleccionada = habilitadas.firstOrNull { it.ambito.servicioId == previa }?.clave
                ?: habilitadas.singleOrNull()?.clave
            publicar(cargando = false)
        }
    }

    /** Nombres del servidor; si alguno llega vacío, el mismo texto honesto del respaldo. */
    private fun deContextos(contextos: List<ContextoPropio>): List<AmbitoConNombres> =
        PoliticaAmbito.ambitosDeContextos(contextos).map { ambito ->
            AmbitoConNombres(
                ambito = ambito,
                hospital = ambito.hospitalNombre ?: EtiquetasPorIdentificador.etiqueta("Hospital", ambito.hospitalId),
                division = ambito.divisionId?.let { ambito.divisionNombre ?: EtiquetasPorIdentificador.etiqueta("División", it) },
                servicio = ambito.servicioId?.let { ambito.servicioNombre ?: EtiquetasPorIdentificador.etiqueta("Servicio", it) },
            )
        }

    /** Respaldo: sesión guardada por M0 (sin asignaciones) no ofrece nada; hay que volver a entrar. */
    private suspend fun deAsignaciones(): List<AmbitoConNombres> {
        val asignaciones = sessionStore.actual()?.asignaciones.orEmpty()
        return PoliticaAmbito.ambitosAutorizados(asignaciones).map { ambito ->
            AmbitoConNombres(
                ambito = ambito,
                hospital = etiquetas.etiquetaHospital(ambito.hospitalId),
                division = ambito.divisionId?.let { etiquetas.etiquetaDivision(it) },
                servicio = ambito.servicioId?.let { etiquetas.etiquetaServicio(it) },
            )
        }
    }

    fun onSeleccionar(clave: String) {
        if (ambitos.value.none { it.clave == clave && it.ambito.habilitado }) return
        seleccionada = clave
        publicar()
    }

    fun onContinuar() {
        val elegido = ambitos.value.firstOrNull { it.clave == seleccionada && it.ambito.habilitado } ?: return
        val servicioId = elegido.ambito.servicioId ?: return
        scopeCache.seleccionarServicio(
            ServicioActivo(
                hospitalId = elegido.ambito.hospitalId,
                divisionId = elegido.ambito.divisionId,
                servicioId = servicioId,
                rol = elegido.ambito.rol,
                facultades = elegido.ambito.facultades,
                hospitalNombre = elegido.hospital,
                divisionNombre = elegido.division,
                servicioNombre = elegido.servicio ?: servicioId,
                zonaHoraria = elegido.ambito.zonaHoraria,
                vigenteHasta = elegido.ambito.vigenteHasta,
            ),
        )
        viewModelScope.launch {
            if (interruptores.funcionesFuturas) seleccionarAmbitoDeM0(servicioId)
            _eventos.send(SelectorAmbitoEvento.Continuar)
        }
    }

    /**
     * Las pantallas futuras (cola offline de M0) leen el ámbito del catálogo Fake con turno. Solo
     * existe si el servicio está en ese catálogo; con UUID reales esas pantallas quedan vacías.
     */
    private suspend fun seleccionarAmbitoDeM0(servicioId: String) {
        val alcance = alcanceRepository.alcancesVigentes().firstOrNull { it.servicio.id == servicioId } ?: return
        val turno = alcanceRepository.turnos(servicioId).firstOrNull() ?: return
        val rol = PoliticaAmbito.rolEfectivo(alcance, sessionStore.actual()?.asignaciones) ?: return
        scopeCache.seleccionar(AmbitoActivo(alcance, turno, rolEfectivo = rol))
    }

    fun onCerrarSesion() {
        if (_uiState.value.cerrandoSesion) return
        _uiState.update { it.copy(cerrandoSesion = true) }
        viewModelScope.launch {
            sesionManager.cerrarSesion()
            _eventos.send(SelectorAmbitoEvento.SesionCerrada)
        }
    }

    private fun publicar(cargando: Boolean = _uiState.value.cargando) {
        _uiState.update { estado ->
            estado.copy(
                cargando = cargando,
                opciones = ambitos.value.map {
                    OpcionAmbito(
                        clave = it.clave,
                        hospital = it.hospital,
                        division = it.division,
                        servicio = it.servicio,
                        rol = it.ambito.rol,
                        capturar = it.ambito.facultades.capturar,
                        consultar = it.ambito.facultades.validar,
                        habilitada = it.ambito.habilitado,
                        seleccionada = it.clave == seleccionada,
                        vigencia = textoVigencia(it.ambito),
                    )
                },
            )
        }
    }

    companion object {
        /** «Vigente hasta 1 dic 00:00» en la zona del hospital (la del contexto o la de por defecto). */
        internal fun textoVigencia(ambito: AmbitoAutorizado): String? {
            val hasta = ambito.vigenteHasta ?: return null
            val zona = HoraHospital.zona(ambito.zonaHoraria).id
            return "Vigente hasta ${HoraHospital.fechaHora(hasta, zona)}"
        }
    }
}
