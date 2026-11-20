package mx.sigeh.app.features.incorporacion

import androidx.lifecycle.SavedStateHandle
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
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.data.CoberturaRepository
import mx.sigeh.app.core.sync.ConnectivityObserver

private data class Formulario(
    val inicioEfectivo: String,
    val enviando: Boolean = false,
    val mostrarRechazo: Boolean = false,
    val motivoRechazo: String = "",
    val error: String? = null,
)

/**
 * Recepción de personal eventual (CU-COB-10/11). Solo en línea: la necesidad cuenta como cubierta
 * únicamente cuando el servicio receptor confirma la incorporación (RN-COB-02).
 */
@HiltViewModel
class RecepcionEventualViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coberturaRepository: CoberturaRepository,
    private val conectividad: ConnectivityObserver,
    scopeCache: ScopeCache,
    reloj: Reloj,
) : ViewModel() {

    private val asignacionId: String = checkNotNull(savedStateHandle["asignacionId"])
    private val formulario = MutableStateFlow(Formulario(inicioEfectivo = FormatoHora.hora(reloj.ahoraMillis())))
    private val ambito = scopeCache.ambitoActual()

    private val _eventos = Channel<RecepcionEvento>(Channel.BUFFERED)
    val eventos: Flow<RecepcionEvento> = _eventos.receiveAsFlow()

    val uiState: StateFlow<RecepcionEventualUiState> = combine(
        coberturaRepository.observarAsignacion(asignacionId),
        conectividad.enLinea,
        formulario,
    ) { asignacion, enLinea, form ->
        RecepcionEventualUiState(
            cargando = false,
            noEncontrada = asignacion == null,
            subtitulo = listOfNotNull(ambito?.alcance?.servicio?.nombre, ambito?.turno?.nombre).joinToString(" · "),
            asignacion = asignacion,
            inicioEfectivo = form.inicioEfectivo,
            enLinea = enLinea,
            tieneFacultad = ambito?.facultades?.recibirEventual ?: false,
            enviando = form.enviando,
            mostrarRechazo = form.mostrarRechazo,
            motivoRechazo = form.motivoRechazo,
            error = form.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecepcionEventualUiState())

    fun onInicioEfectivo(hora: String) = formulario.update { it.copy(inicioEfectivo = hora) }

    fun onConfirmar() {
        if (!uiState.value.accionesHabilitadas) return
        ejecutar { coberturaRepository.confirmarIncorporacion(asignacionId, formulario.value.inicioEfectivo) }
    }

    fun onMostrarRechazo(mostrar: Boolean) = formulario.update { it.copy(mostrarRechazo = mostrar, motivoRechazo = "") }

    fun onMotivoRechazo(texto: String) = formulario.update { it.copy(motivoRechazo = texto.take(MAX_MOTIVO)) }

    fun onRechazar() {
        val motivo = formulario.value.motivoRechazo.trim()
        if (!uiState.value.accionesHabilitadas || motivo.isEmpty()) return
        ejecutar { coberturaRepository.rechazarIncorporacion(asignacionId, motivo) }
    }

    private fun ejecutar(accion: suspend () -> Boolean) {
        formulario.update { it.copy(enviando = true, error = null, mostrarRechazo = false) }
        viewModelScope.launch {
            // Se vuelve a comprobar la red justo antes: esta acción nunca se encola sin conexión.
            val ok = conectividad.enLinea.value && accion()
            formulario.update {
                it.copy(enviando = false, error = if (ok) null else "No se pudo registrar. Revisa tu conexión e inténtalo de nuevo.")
            }
            if (ok) _eventos.send(RecepcionEvento.Terminada)
        }
    }

    private companion object {
        const val MAX_MOTIVO = 200
    }
}
