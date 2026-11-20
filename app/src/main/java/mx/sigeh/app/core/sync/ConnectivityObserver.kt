package mx.sigeh.app.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import mx.sigeh.app.core.common.ApplicationScope
import mx.sigeh.app.core.common.Reloj

/** Estado de la red para el `ConnectionBanner` y las reglas offline. */
interface ConnectivityObserver {
    val enLinea: StateFlow<Boolean>

    /** Momento en que se perdió la red (null si hay conexión). */
    val sinConexionDesde: StateFlow<Long?>
}

/** `NetworkCallback` → `Flow<Boolean>`, compartido en toda la app. */
@Singleton
class AndroidConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
    private val reloj: Reloj,
) : ConnectivityObserver {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    private val _sinConexionDesde = MutableStateFlow(if (estadoActual()) null else reloj.ahoraMillis())
    override val sinConexionDesde: StateFlow<Long?> = _sinConexionDesde.asStateFlow()

    override val enLinea: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(estadoActual())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(estadoActual())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .onEach { conectado ->
            _sinConexionDesde.value = if (conectado) null else _sinConexionDesde.value ?: reloj.ahoraMillis()
        }
        .stateIn(scope, SharingStarted.Eagerly, estadoActual())

    private fun estadoActual(): Boolean {
        val red = connectivityManager.activeNetwork ?: return false
        val capacidades = connectivityManager.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
