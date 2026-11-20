package mx.sigeh.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.EstadoSesion
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.network.NetworkConfig
import mx.sigeh.app.core.sync.SyncScheduler

sealed interface EventoApp {
    data object AlcanceVencido : EventoApp
}

/**
 * Estado global de la app: decide el destino inicial, reacciona a la sesión vencida (401/403)
 * y, solo en modo Fake, purga los datos locales cuando vence el alcance activo.
 *
 * - La cola offline de M0 se reanuda solo con `FUNCIONES_FUTURAS` (la captura de M2 no la usa).
 * - Con IAM real `/auth/me` no trae vigencias: la vigencia la decide el servidor con un 403.
 */
@HiltViewModel
class SigehAppViewModel @Inject constructor(
    sessionStore: SessionStore,
    private val scopeCache: ScopeCache,
    private val sesionManager: SesionManager,
    private val reloj: Reloj,
    private val syncScheduler: SyncScheduler,
    private val interruptores: Interruptores,
    private val networkConfig: NetworkConfig,
) : ViewModel() {

    val estadoSesion: StateFlow<EstadoSesion> = sessionStore.session
        .map { sesion ->
            when {
                sesion == null -> EstadoSesion.SIN_SESION
                sesion.vencida -> EstadoSesion.VENCIDA
                else -> EstadoSesion.ACTIVA
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.CARGANDO)

    private val _eventos = Channel<EventoApp>(Channel.BUFFERED)
    val eventos: Flow<EventoApp> = _eventos.receiveAsFlow()

    /** Muestra incidencias, movimientos y demás funciones sin backend (`sigeh.features.futuras`). */
    val funcionesFuturas: Boolean get() = interruptores.funcionesFuturas

    /** Pestaña Programación: solo con `PROGRAMACION_CONSULTAR` en el servicio elegido. */
    val programacionVisible: StateFlow<Boolean> = scopeCache.servicio
        .map { it?.facultades?.consultarProgramacion == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        if (interruptores.funcionesFuturas) {
            // Al abrir la app con sesión activa se reanuda la cola que quedó pendiente (trabajo único KEEP).
            viewModelScope.launch {
                estadoSesion.first { it != EstadoSesion.CARGANDO }
                if (estadoSesion.value == EstadoSesion.ACTIVA) syncScheduler.programar()
            }
        }
        if (!networkConfig.iamReal) vigilarVigenciaDelAlcance()
    }

    /** Solo Fake: el catálogo ficticio sí trae vigencias. */
    private fun vigilarVigenciaDelAlcance() {
        viewModelScope.launch {
            while (isActive) {
                val ambito = scopeCache.ambitoActual()
                if (ambito != null && !ambito.alcance.estaVigente(reloj.ahoraMillis())) {
                    sesionManager.purgarPorAlcanceVencido()
                    _eventos.send(EventoApp.AlcanceVencido)
                }
                delay(INTERVALO_REVISION_MS)
            }
        }
    }

    private companion object {
        const val INTERVALO_REVISION_MS = 60_000L
    }
}
