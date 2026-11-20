package mx.sigeh.app.features.sincronizacion

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.data.PaseListaOfflineRepository
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.ComandoLocal
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.DecisionConflicto
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.RegistroAsistenciaServidor
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.core.sync.ConflictResolver

private data class Seleccion(
    val opcion: DecisionConflicto = DecisionConflicto.CONSERVAR_SERVIDOR,
    val motivo: String = "",
    val enviando: Boolean = false,
)

/** Resolver conflicto (RN-ASI-05). Nunca reemplaza el registro validado del servidor. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConflictoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    colaComandos: ColaComandos,
    private val paseListaRepository: PaseListaOfflineRepository,
    private val conflictResolver: ConflictResolver,
    private val scopeCache: ScopeCache,
) : ViewModel() {

    private val clientEventId: String = checkNotNull(savedStateHandle["clientEventId"])
    private val seleccion = MutableStateFlow(Seleccion())

    private val _eventos = Channel<ConflictoEvento>(Channel.BUFFERED)
    val eventos: Flow<ConflictoEvento> = _eventos.receiveAsFlow()

    private var etiqueta: String = ""

    val uiState: StateFlow<ConflictoUiState> = colaComandos.observar(clientEventId)
        .flatMapLatest { comando ->
            if (comando == null) return@flatMapLatest flowOf(DatosConflicto())
            val ambito = scopeCache.ambitoActual()
            val pase = ambito?.let { paseListaRepository.observarPase(it.alcance.servicio.id, it.turno.id) } ?: flowOf(null)
            combine(
                flow { emit(paseListaRepository.jornada(comando.programacionId)) },
                paseListaRepository.observarRegistroServidor(comando.programacionId),
                pase,
            ) { jornada, registro, p -> DatosConflicto(comando, jornada, registro, p?.fechaCorta) }
        }
        .combine(seleccion) { datos, sel -> construir(datos, sel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConflictoUiState())

    fun onOpcion(opcion: DecisionConflicto) = seleccion.update { it.copy(opcion = opcion) }

    fun onMotivoChange(motivo: String) = seleccion.update { it.copy(motivo = motivo.take(MAX_MOTIVO)) }

    fun onConfirmar() {
        val estado = uiState.value
        if (!estado.puedeConfirmar) return
        seleccion.update { it.copy(enviando = true) }
        viewModelScope.launch {
            val ok = conflictResolver.resolver(clientEventId, estado.opcion, estado.motivo, etiqueta)
            seleccion.update { it.copy(enviando = false) }
            if (ok) _eventos.send(ConflictoEvento.Resuelto)
        }
    }

    private fun construir(datos: DatosConflicto, sel: Seleccion): ConflictoUiState {
        val (comando, jornada, registro, fecha) = datos
        if (comando == null || comando.estado != CommandStatus.CONFLICTO) {
            return ConflictoUiState(cargando = false, noDisponible = true)
        }
        etiqueta = jornada?.nombreCorto.orEmpty()
        val estadoLocal = runCatching {
            SigehJson.decodeFromString(AsistenciaPayload.serializer(), comando.payloadJson).estado
        }.getOrNull()
        val turno = scopeCache.ambitoActual()?.turno?.nombre
        return ConflictoUiState(
            cargando = false,
            subtitulo = listOfNotNull(jornada?.nombre, turno, fecha).joinToString(" · "),
            etiquetaPersona = etiqueta,
            local = estadoLocal?.let {
                LadoConflicto(it, "Capturado por ti a las ${FormatoHora.hora(comando.creadoEnMillis)}")
            },
            servidor = registro?.let {
                LadoConflicto(
                    it.estado,
                    buildString {
                        append(if (it.validado) "Registrado y validado" else "Registrado")
                        it.validadoPor?.let { quien -> append(" por $quien") }
                        it.horaValidacion?.let { hora -> append(" a las $hora") }
                    },
                )
            },
            servidorValidado = registro?.validado == true,
            opcion = sel.opcion,
            motivo = sel.motivo,
            enviando = sel.enviando,
        )
    }

    private companion object {
        const val MAX_MOTIVO = 300
    }
}

private data class DatosConflicto(
    val comando: ComandoLocal? = null,
    val jornada: JornadaProgramada? = null,
    val registro: RegistroAsistenciaServidor? = null,
    val fecha: String? = null,
)
