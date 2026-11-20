package mx.sigeh.app.features.selectorambito

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.AsignacionSesion
import mx.sigeh.app.core.auth.PoliticaAmbito
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.data.AlcanceRepository
import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.AmbitoActivo
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.model.Turno
import mx.sigeh.app.core.sync.ColaComandos

private data class DatosSelector(
    val cargando: Boolean = true,
    val nombreUsuario: String = "",
    val alcances: List<Alcance> = emptyList(),
    val turnos: List<Turno> = emptyList(),
    val alcanceId: String? = null,
    val turnoId: String? = null,
    val cerrandoSesion: Boolean = false,
    /** Asignaciones reales de IAM V2; null en modo Fake. */
    val asignaciones: List<AsignacionSesion>? = null,
)

@HiltViewModel
class SelectorAmbitoViewModel @Inject constructor(
    private val alcanceRepository: AlcanceRepository,
    private val scopeCache: ScopeCache,
    private val sessionStore: SessionStore,
    private val sesionManager: SesionManager,
    colaComandos: ColaComandos,
) : ViewModel() {

    private val datos = MutableStateFlow(DatosSelector())

    private val _eventos = Channel<SelectorAmbitoEvento>(Channel.BUFFERED)
    val eventos: Flow<SelectorAmbitoEvento> = _eventos.receiveAsFlow()

    val uiState: StateFlow<SelectorAmbitoUiState> =
        combine(datos, colaComandos.observarPorEnviar()) { d, pendientes -> construir(d, pendientes) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SelectorAmbitoUiState())

    init {
        viewModelScope.launch {
            val sesion = sessionStore.actual()
            val asignaciones = sesion?.asignaciones
            // Denegar por defecto: solo se ofrecen ámbitos con un rol efectivo según IAM.
            val alcances = alcanceRepository.alcancesVigentes()
                .filter { PoliticaAmbito.rolEfectivo(it, asignaciones) != null }
            scopeCache.guardarAlcances(alcances)
            val previo = scopeCache.ambitoActual()
            val alcanceInicial = previo?.alcance?.id ?: alcances.firstOrNull()?.id
            val turnos = alcanceInicial
                ?.let { id -> alcances.firstOrNull { it.id == id } }
                ?.let { alcanceRepository.turnos(it.servicio.id) }
                .orEmpty()
            datos.update {
                it.copy(
                    cargando = false,
                    nombreUsuario = sesion?.nombreCorto.orEmpty(),
                    asignaciones = asignaciones,
                    alcances = alcances,
                    turnos = turnos,
                    alcanceId = alcanceInicial,
                    turnoId = previo?.turno?.id ?: turnos.firstOrNull()?.id,
                )
            }
        }
    }

    fun onServicioSeleccionado(alcanceId: String) {
        val alcance = datos.value.alcances.firstOrNull { it.id == alcanceId } ?: return
        viewModelScope.launch {
            val turnos = alcanceRepository.turnos(alcance.servicio.id)
            datos.update { actual ->
                actual.copy(
                    alcanceId = alcanceId,
                    turnos = turnos,
                    turnoId = actual.turnoId?.takeIf { id -> turnos.any { it.id == id } } ?: turnos.firstOrNull()?.id,
                )
            }
        }
    }

    fun onTurnoSeleccionado(turnoId: String) = datos.update { it.copy(turnoId = turnoId) }

    fun onContinuar() {
        val d = datos.value
        val alcance = d.alcances.firstOrNull { it.id == d.alcanceId } ?: return
        val turno = d.turnos.firstOrNull { it.id == d.turnoId } ?: return
        val rol = PoliticaAmbito.rolEfectivo(alcance, d.asignaciones) ?: return
        scopeCache.seleccionar(AmbitoActivo(alcance, turno, rolEfectivo = rol))
        viewModelScope.launch { _eventos.send(SelectorAmbitoEvento.Continuar) }
    }

    fun onCerrarSesion() {
        if (datos.value.cerrandoSesion) return
        datos.update { it.copy(cerrandoSesion = true) }
        viewModelScope.launch {
            sesionManager.cerrarSesion()
            _eventos.send(SelectorAmbitoEvento.SesionCerrada)
        }
    }

    private fun construir(d: DatosSelector, pendientes: Int): SelectorAmbitoUiState {
        val hospitales = d.alcances.map { it.hospital }.distinctBy { it.id }
        return SelectorAmbitoUiState(
            cargando = d.cargando,
            nombreUsuario = d.nombreUsuario,
            hospitalNombre = hospitales.firstOrNull()?.nombre.orEmpty(),
            hospitalDetalle = if (hospitales.size == 1) {
                "Único hospital autorizado para tu usuario"
            } else {
                "${hospitales.size} hospitales autorizados"
            },
            servicios = d.alcances.map { alcance ->
                val titular = PoliticaAmbito.rolEfectivo(alcance, d.asignaciones) == RolAlcance.TITULAR
                OpcionServicio(
                    alcanceId = alcance.id,
                    nombre = alcance.servicio.nombre,
                    division = alcance.servicio.divisionNombre,
                    rolTexto = when {
                        titular -> "${alcance.cargo} · titular"
                        alcance.rol == RolAlcance.ENCARGADO -> "${alcance.cargo} · facultades limitadas"
                        // Titular en el catálogo, pero IAM solo informa encargos: se limita.
                        else -> "Encargo · facultades limitadas"
                    },
                    titular = titular,
                    seleccionado = alcance.id == d.alcanceId,
                )
            },
            sinAsignaciones = !d.cargando && d.alcances.isEmpty(),
            turnos = d.turnos.map { OpcionTurno(it.id, it.nombre, it.horarioTexto, it.id == d.turnoId) },
            pendientesSinEnviar = pendientes,
            cerrandoSesion = d.cerrandoSesion,
        )
    }
}
