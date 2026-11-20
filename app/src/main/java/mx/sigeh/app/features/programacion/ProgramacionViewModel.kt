package mx.sigeh.app.features.programacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ProgramacionRepository
import mx.sigeh.app.core.data.ResultadoProgramacion
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.JornadaSemanal

data class DiaSemanaUi(val fecha: String, val texto: String, val jornadas: Int, val seleccionado: Boolean)

data class FilaProgramacion(val programacionId: String, val nombre: String, val horario: String, val estado: EstadoProgramacion)

data class GrupoTurno(val turno: String, val filas: List<FilaProgramacion>)

enum class BloqueoProgramacion { SIN_PERMISO, NO_HABILITADA, SESION_TERMINADA }

data class ProgramacionUiState(
    val cargando: Boolean = true,
    val servicio: String = "",
    val semana: String = "",
    val dias: List<DiaSemanaUi> = emptyList(),
    val grupos: List<GrupoTurno> = emptyList(),
    val nombresDisponibles: Boolean = true,
    val error: Boolean = false,
    val bloqueo: BloqueoProgramacion? = null,
)

/**
 * Pantalla 9.8 · Programación del servicio. Ventana de lunes 00:00 a lunes 00:00 en la zona del
 * hospital (máximo 168 h). Muestra lo programado, no la asistencia ni la cobertura real.
 */
@HiltViewModel
class ProgramacionViewModel @Inject constructor(
    private val scopeCache: ScopeCache,
    private val repositorio: ProgramacionRepository,
    paseListaRepository: PaseListaRepository,
    private val sessionStore: SessionStore,
    private val reloj: Reloj,
) : ViewModel() {

    private val zona = paseListaRepository.zonaHoraria() ?: HoraHospital.ZONA_POR_DEFECTO
    private val hoy = HoraHospital.hoy(reloj.ahoraMillis(), zona)
    private var lunes = HoraHospital.lunesDe(hoy)
    private var dia = hoy
    private var jornadas: List<JornadaSemanal> = emptyList()
    private var carga: Job? = null

    private val _uiState = MutableStateFlow(ProgramacionUiState(servicio = scopeCache.servicioActual()?.servicioNombre.orEmpty()))
    val uiState: StateFlow<ProgramacionUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun onSemanaAnterior() = moverSemana(-7)

    fun onSemanaSiguiente() = moverSemana(7)

    fun onDiaSeleccionado(fecha: String) {
        dia = fecha
        publicar()
    }

    fun onReintentar() = cargar()

    private fun moverSemana(dias: Int) {
        val desplazamiento = HoraHospital.diasEntre(lunes, dia)
        lunes = HoraHospital.sumarDias(lunes, dias)
        dia = HoraHospital.sumarDias(lunes, desplazamiento)
        cargar()
    }

    private fun cargar() {
        val servicio = scopeCache.servicioActual() ?: return
        carga?.cancel()
        jornadas = emptyList()
        _uiState.update { it.copy(cargando = true, error = false, bloqueo = null, semana = HoraHospital.rangoSemana(lunes)) }
        publicar()
        carga = viewModelScope.launch {
            val (desde, hasta) = HoraHospital.ventanaSemana(lunes, zona)
            val resultado = repositorio.semana(
                hospitalId = servicio.hospitalId,
                servicioId = servicio.servicioId,
                desde = desde,
                hasta = hasta,
                conNombres = servicio.facultades.consultarPersonal,
                fechaPadron = lunes,
            )
            when (resultado) {
                is ResultadoProgramacion.Exito -> {
                    jornadas = resultado.jornadas
                    _uiState.update { it.copy(cargando = false, nombresDisponibles = resultado.nombresDisponibles) }
                }
                ResultadoProgramacion.SinPermiso -> _uiState.update { it.copy(cargando = false, bloqueo = BloqueoProgramacion.SIN_PERMISO) }
                ResultadoProgramacion.NoHabilitada -> _uiState.update { it.copy(cargando = false, bloqueo = BloqueoProgramacion.NO_HABILITADA) }
                ResultadoProgramacion.SesionTerminada -> {
                    _uiState.update { it.copy(cargando = false, bloqueo = BloqueoProgramacion.SESION_TERMINADA) }
                    sessionStore.marcarVencida()
                }
                ResultadoProgramacion.Fallida -> _uiState.update { it.copy(cargando = false, error = true) }
            }
            publicar()
        }
    }

    private fun fechaLocal(jornada: JornadaSemanal): String? =
        HoraHospital.instante(jornada.inicioProgramado)?.let { HoraHospital.fechaDe(it, zona) }

    private fun publicar() {
        val porDia = jornadas.groupBy { fechaLocal(it) }
        val delDia = porDia[dia].orEmpty()
        _uiState.update { estado ->
            estado.copy(
                semana = HoraHospital.rangoSemana(lunes),
                dias = (0 until 7).map { i ->
                    val fecha = HoraHospital.sumarDias(lunes, i)
                    DiaSemanaUi(fecha, HoraHospital.diaCorto(fecha), porDia[fecha]?.size ?: 0, fecha == dia)
                },
                grupos = delDia
                    .sortedBy { HoraHospital.instante(it.inicioProgramado) ?: 0 }
                    .groupBy { it.turnoCodigo ?: "Sin turno" }
                    .map { (turno, lista) ->
                        GrupoTurno(
                            turno = turno,
                            filas = lista.map {
                                FilaProgramacion(
                                    programacionId = it.programacionId,
                                    nombre = it.nombreVisible,
                                    horario = HoraHospital.intervalo(it.inicioProgramado, it.finProgramado, zona),
                                    estado = it.estado,
                                )
                            },
                        )
                    },
            )
        }
    }
}
