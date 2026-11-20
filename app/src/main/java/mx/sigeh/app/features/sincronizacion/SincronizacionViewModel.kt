package mx.sigeh.app.features.sincronizacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.data.AlcanceRepository
import mx.sigeh.app.core.database.dao.SyncReceiptDao
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.core.sync.ConnectivityObserver

/** Estado de sincronización (CU-ASI-13, CU-OPS-04): errores visibles por elemento. */
@HiltViewModel
class SincronizacionViewModel @Inject constructor(
    private val colaComandos: ColaComandos,
    private val conectividad: ConnectivityObserver,
    private val alcanceRepository: AlcanceRepository,
    private val scopeCache: ScopeCache,
    syncReceiptDao: SyncReceiptDao,
    sessionStore: SessionStore,
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)
    private val nombresServicio = MutableStateFlow<Map<String, String>>(emptyMap())
    private val sesionVencida = MutableStateFlow(false)

    private val comandos = colaComandos.observarTodos().onEach { lista ->
        lista.map { it.servicioId }.distinct()
            .filterNot { it in nombresServicio.value }
            .forEach { id ->
                alcanceRepository.nombreServicio(id)?.let { nombre -> nombresServicio.update { it + (id to nombre) } }
            }
    }

    private val contexto = combine(
        conectividad.enLinea,
        conectividad.sinConexionDesde,
        syncReceiptDao.observarTodos().map { recibos -> recibos.associate { it.clientEventId to it.appliedAtServer } },
        sessionStore.session.onEach { sesionVencida.value = it?.vencida == true },
        nombresServicio,
    ) { enLinea, desde, recibos, _, nombres -> ContextoSync(enLinea, desde, recibos, nombres) }

    val uiState: StateFlow<SincronizacionUiState> =
        combine(comandos, contexto, sesionVencida, mensaje) { lista, ctx, vencida, texto ->
            val servicioActivo = scopeCache.ambitoActual()?.alcance?.servicio?.id
            SincronizacionUiState(
                cargando = false,
                enLinea = ctx.enLinea,
                sinConexionDesde = ctx.sinConexionDesde?.let(FormatoHora::hora),
                ultimoEnvioCorrecto = ctx.recibos.values.filterNotNull().maxOrNull()?.let(FormatoHora::hora),
                porEnviar = lista.count { it.estado.porEnviar },
                conConflicto = lista.count { it.estado == CommandStatus.CONFLICTO },
                aplicados = lista.count { it.estado.aplicado },
                sesionVencida = vencida,
                registros = ordenarRegistros(lista).map { comando ->
                    describirRegistro(
                        comando = comando,
                        enLinea = ctx.enLinea,
                        aplicadoEnMillis = ctx.recibos[comando.clientEventId],
                        servicioAjeno = ctx.nombres[comando.servicioId]
                            ?.takeIf { servicioActivo != null && comando.servicioId != servicioActivo },
                    )
                },
                mensaje = texto,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SincronizacionUiState())

    fun onReintentar() {
        mensaje.value = when {
            sesionVencida.value -> "Tu sesión venció. Inicia sesión de nuevo para reanudar el envío."
            !conectividad.enLinea.value -> "Sin conexión: se enviará automáticamente al volver la red."
            else -> {
                colaComandos.reintentarAhora()
                "Reintentando el envío…"
            }
        }
    }

    fun onMensajeMostrado() {
        mensaje.value = null
    }

    private data class ContextoSync(
        val enLinea: Boolean,
        val sinConexionDesde: Long?,
        val recibos: Map<String, Long?>,
        val nombres: Map<String, String>,
    )
}
