package mx.sigeh.app.features.cuenta

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
import mx.sigeh.app.BuildConfig
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ResultadoRevocacion
import mx.sigeh.app.core.data.ResultadoSesiones
import mx.sigeh.app.core.data.ServidorRepository
import mx.sigeh.app.core.data.SesionIam
import mx.sigeh.app.core.data.SesionesRepository

data class FilaSesion(
    val sessionId: String,
    /** «Iniciada 20 nov 07:00 · Expira 20 nov 15:00 · inactividad 20 nov 07:30». */
    val detalle: String,
    val actual: Boolean,
    val revocada: Boolean,
    val cerrando: Boolean,
)

data class CuentaUiState(
    val identificador: String = "",
    val servicio: String = "",
    val hospital: String = "",
    val sesiones: List<FilaSesion> = emptyList(),
    val cargandoSesiones: Boolean = true,
    val errorSesiones: String? = null,
    val cerrandoSesion: Boolean = false,
    /** «App 0.2.0-m2 · contrato API 1.0.0». */
    val pie: String = "",
    val funcionesFuturas: Boolean = false,
)

sealed interface CuentaEvento {
    data object SesionCerrada : CuentaEvento
}

/** Pantalla 9.9 · Cuenta: sesiones de IAM (`/auth/sessions`) y cierre de sesión. */
@HiltViewModel
class CuentaViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    scopeCache: ScopeCache,
    private val sesiones: SesionesRepository,
    private val sesionManager: SesionManager,
    private val servidorRepository: ServidorRepository,
    paseListaRepository: PaseListaRepository,
    interruptores: Interruptores,
) : ViewModel() {

    private val zona = paseListaRepository.zonaHoraria() ?: HoraHospital.ZONA_POR_DEFECTO
    private val revocando = mutableSetOf<String>()
    private var lista: List<SesionIam> = emptyList()

    private val _uiState = MutableStateFlow(
        CuentaUiState(
            servicio = scopeCache.servicioActual()?.servicioNombre.orEmpty(),
            hospital = scopeCache.servicioActual()?.hospitalNombre.orEmpty(),
            pie = pie(contrato = null),
            funcionesFuturas = interruptores.funcionesFuturas,
        ),
    )
    val uiState: StateFlow<CuentaUiState> = _uiState.asStateFlow()

    private val _eventos = Channel<CuentaEvento>(Channel.BUFFERED)
    val eventos: Flow<CuentaEvento> = _eventos.receiveAsFlow()

    init {
        viewModelScope.launch {
            val sesion = sessionStore.actual()
            _uiState.update { it.copy(identificador = sesion?.identificador ?: sesion?.nombreCorto.orEmpty()) }
        }
        viewModelScope.launch {
            val contrato = if (servidorRepository.modoReal) servidorRepository.versionContrato() else null
            _uiState.update { it.copy(pie = pie(contrato)) }
        }
        cargarSesiones()
    }

    fun cargarSesiones() = recargarSesiones(aviso = null)

    /** [aviso] se muestra después de recargar (un aviso puesto antes lo borraría la recarga). */
    private fun recargarSesiones(aviso: String?) {
        _uiState.update { it.copy(cargandoSesiones = true, errorSesiones = aviso) }
        viewModelScope.launch {
            when (val r = sesiones.sesiones()) {
                is ResultadoSesiones.Exito -> {
                    lista = r.sesiones
                    _uiState.update { it.copy(cargandoSesiones = false) }
                }
                ResultadoSesiones.SesionTerminada -> sessionStore.marcarVencida()
                ResultadoSesiones.Fallida ->
                    _uiState.update { it.copy(cargandoSesiones = false, errorSesiones = "No se pudieron cargar las sesiones.") }
            }
            publicarSesiones()
        }
    }

    /** «Cerrar» una sesión. Si era la de este dispositivo, se trata como cierre de sesión y se purga todo. */
    fun onRevocar(sessionId: String) {
        if (!revocando.add(sessionId)) return
        publicarSesiones()
        viewModelScope.launch {
            val resultado = sesiones.revocar(sessionId)
            revocando.remove(sessionId)
            when (resultado) {
                is ResultadoRevocacion.Revocada ->
                    if (resultado.eraLaActual) {
                        cerrarSesion()
                        return@launch
                    } else {
                        cargarSesiones()
                    }
                ResultadoRevocacion.NoEncontrada -> recargarSesiones(aviso = "Esa sesión ya no está disponible.")
                ResultadoRevocacion.SesionTerminada -> sessionStore.marcarVencida()
                ResultadoRevocacion.Fallida -> _uiState.update { it.copy(errorSesiones = "No se pudo cerrar la sesión. Intenta de nuevo.") }
            }
            publicarSesiones()
        }
    }

    /** «Cerrar sesión»: `POST /auth/logout` y purga local aunque el servidor no responda. */
    fun onCerrarSesion() {
        if (_uiState.value.cerrandoSesion) return
        viewModelScope.launch { cerrarSesion() }
    }

    private suspend fun cerrarSesion() {
        _uiState.update { it.copy(cerrandoSesion = true) }
        sesionManager.cerrarSesion()
        _eventos.send(CuentaEvento.SesionCerrada)
    }

    private fun publicarSesiones() {
        _uiState.update { estado ->
            estado.copy(
                sesiones = lista.map {
                    FilaSesion(
                        sessionId = it.sessionId,
                        detalle = "Iniciada ${HoraHospital.fechaHora(it.iniciadaEn, zona)} · " +
                            "Expira ${HoraHospital.fechaHora(it.expiraEn, zona)} · " +
                            "inactividad ${HoraHospital.fechaHora(it.inactividadExpiraEn, zona)}",
                        actual = it.actual,
                        revocada = it.revocada,
                        cerrando = it.sessionId in revocando,
                    )
                },
            )
        }
    }

    private fun pie(contrato: String?): String =
        "App ${BuildConfig.VERSION_NAME} · " + if (contrato != null) "contrato API $contrato" else "modo demostración"
}
