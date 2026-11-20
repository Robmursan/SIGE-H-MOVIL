package mx.sigeh.app.features.paselista

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.model.AmbitoActivo
import mx.sigeh.app.core.model.ComandoLocal
import mx.sigeh.app.core.model.ContextoCaptura
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.PaseLista
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.core.sync.ConnectivityObserver

/**
 * Pase de lista (CU-ASI-01/02/03/13). Tocar un estado crea o actualiza un comando
 * PENDIENTE_LOCAL en Room; la fila pasa a "En el teléfono" hasta que el servidor lo aplique.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PaseListaViewModel @Inject constructor(
    private val scopeCache: ScopeCache,
    private val paseListaRepository: PaseListaRepository,
    private val colaComandos: ColaComandos,
    private val sessionStore: SessionStore,
    private val reloj: Reloj,
    conectividad: ConnectivityObserver,
) : ViewModel() {

    private val mensaje = MutableStateFlow<String?>(null)
    private val paseActual = MutableStateFlow<PaseLista?>(null)

    private val _eventos = Channel<PaseListaEvento>(Channel.BUFFERED)
    val eventos: Flow<PaseListaEvento> = _eventos.receiveAsFlow()

    val uiState: StateFlow<PaseListaUiState> = scopeCache.activo
        .flatMapLatest { ambito ->
            if (ambito == null) {
                flowOf(PaseListaUiState(cargando = false, sinProgramacion = true))
            } else {
                combine(
                    paseListaRepository.observarPase(ambito.alcance.servicio.id, ambito.turno.id)
                        .onEach { paseActual.value = it },
                    colaComandos.observarTodos(),
                    conectividad.enLinea,
                    mensaje,
                ) { pase, comandos, enLinea, texto -> construirEstado(ambito, pase, comandos, enLinea, texto) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaseListaUiState())

    fun onEstadoSeleccionado(programacionId: String, estado: EstadoAsistencia) {
        val ambito = scopeCache.ambitoActual() ?: return
        if (!ambito.facultades.capturarPase) return
        val pase = paseActual.value ?: return
        val jornada = pase.jornadas.firstOrNull { it.programacionId == programacionId && it.exigible } ?: return
        viewModelScope.launch {
            val sesion = sessionStore.actual() ?: return@launch
            colaComandos.registrarAsistencia(
                contexto = ContextoCaptura(
                    actorId = sesion.usuarioId,
                    hospitalId = ambito.alcance.hospital.id,
                    servicioId = ambito.alcance.servicio.id,
                    programacionVersion = pase.programacionVersion,
                ),
                jornada = jornada,
                estado = estado,
            )
        }
    }

    fun onGuardarBorrador() {
        val pase = paseActual.value ?: return
        viewModelScope.launch {
            paseListaRepository.guardarBorrador(pase.id)
            mensaje.value = "Borrador guardado en este teléfono. Aún no se envía a validar."
        }
    }

    fun onEnviarAValidar() {
        val pase = paseActual.value ?: return
        viewModelScope.launch {
            paseListaRepository.solicitarEnvioAValidacion(pase.id, reloj.ahoraMillis())
            colaComandos.reintentarAhora()
            _eventos.send(PaseListaEvento.AbrirResumen)
        }
    }

    fun onMensajeMostrado() {
        mensaje.value = null
    }

    private fun construirEstado(
        ambito: AmbitoActivo,
        pase: PaseLista?,
        comandos: List<ComandoLocal>,
        enLinea: Boolean,
        texto: String?,
    ): PaseListaUiState {
        val filas = pase?.let { construirFilas(it, comandos) }.orEmpty()
        return PaseListaUiState(
            cargando = false,
            sinProgramacion = pase == null,
            contexto = contextoPase(ambito),
            subcontexto = subcontextoPase(ambito, pase),
            enLinea = enLinea,
            porEnviar = comandos.count { it.estado.porEnviar },
            filas = filas,
            contadores = calcularContadores(filas),
            puedeCapturar = ambito.facultades.capturarPase,
            mensaje = texto,
        )
    }
}
