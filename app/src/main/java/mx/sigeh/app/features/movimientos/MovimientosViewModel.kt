package mx.sigeh.app.features.movimientos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.data.MovimientosRepository
import mx.sigeh.app.core.data.RespuestaConvenio
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.core.sync.ConnectivityObserver

private data class EstadoLocal(
    val filtro: FiltroMovimientos = FiltroMovimientos.DEL_SERVICIO,
    val respondiendo: String? = null,
    val mensaje: String? = null,
)

/** Movimientos del servicio y acuse de convenio (CU-MOV-02/03/22). Aceptar solo cuenta en línea. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovimientosViewModel @Inject constructor(
    scopeCache: ScopeCache,
    private val movimientosRepository: MovimientosRepository,
    private val conectividad: ConnectivityObserver,
    colaComandos: ColaComandos,
) : ViewModel() {

    private val local = MutableStateFlow(EstadoLocal())

    private val movimientos = scopeCache.activo.flatMapLatest { ambito ->
        if (ambito == null) flowOf(emptyList()) else movimientosRepository.observarMovimientosDelServicio(ambito.alcance.servicio.id)
    }

    val uiState: StateFlow<MovimientosUiState> = combine(
        movimientos,
        movimientosRepository.observarConveniosParaMi(),
        colaComandos.observarPorEnviar(),
        conectividad.enLinea,
        local,
    ) { lista, convenios, porEnviar, enLinea, estado ->
        MovimientosUiState(
            cargando = false,
            filtro = estado.filtro,
            porEnviar = porEnviar,
            enLinea = enLinea,
            movimientos = lista.map { it.aUi() },
            convenios = convenios,
            respondiendo = estado.respondiendo,
            puedeRegistrarSalida = scopeCache.ambitoActual()?.facultades?.registrarSalida ?: false,
            mensaje = estado.mensaje,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovimientosUiState())

    fun onFiltro(filtro: FiltroMovimientos) = local.update { it.copy(filtro = filtro) }

    fun onResponder(movimientoId: String, aceptar: Boolean) {
        if (local.value.respondiendo != null) return
        local.update { it.copy(respondiendo = movimientoId) }
        viewModelScope.launch {
            val resultado = movimientosRepository.responderConvenio(movimientoId, aceptar, conectividad.enLinea.value)
            val texto = when (resultado) {
                RespuestaConvenio.ENVIADA -> if (aceptar) "Aceptaste el convenio. El servidor lo recibió." else "Rechazaste el convenio."
                RespuestaConvenio.GUARDADA_COMO_BORRADOR -> "Sin conexión: tu respuesta quedó como borrador. Confírmala cuando tengas red."
            }
            local.update { it.copy(respondiendo = null, mensaje = texto) }
        }
    }

    fun onMensajeMostrado() = local.update { it.copy(mensaje = null) }
}
