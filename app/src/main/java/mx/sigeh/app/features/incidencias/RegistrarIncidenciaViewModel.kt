package mx.sigeh.app.features.incidencias

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.data.IncidenciaRepository
import mx.sigeh.app.core.data.PaseListaOfflineRepository
import mx.sigeh.app.core.model.ContextoCaptura
import mx.sigeh.app.core.sync.ColaComandos

/**
 * Registro preliminar de incidencia (CU-INC-03/07). Guardar encola un comando en Room;
 * Supervisión o RRHH la clasifican y autorizan en la web.
 */
@HiltViewModel
class RegistrarIncidenciaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val paseListaRepository: PaseListaOfflineRepository,
    private val incidenciaRepository: IncidenciaRepository,
    private val colaComandos: ColaComandos,
    private val scopeCache: ScopeCache,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val programacionId: String = checkNotNull(savedStateHandle["jornadaId"])

    private val _uiState = MutableStateFlow(RegistrarIncidenciaUiState())
    val uiState: StateFlow<RegistrarIncidenciaUiState> = _uiState.asStateFlow()

    private val _eventos = Channel<RegistrarIncidenciaEvento>(Channel.BUFFERED)
    val eventos: Flow<RegistrarIncidenciaEvento> = _eventos.receiveAsFlow()

    private var programacionVersion: Int? = null

    init {
        viewModelScope.launch {
            val ambito = scopeCache.ambitoActual()
            val jornada = paseListaRepository.jornada(programacionId)
            if (ambito == null || jornada == null) {
                _uiState.update { it.copy(cargando = false, noEncontrada = true) }
                return@launch
            }
            val pase = paseListaRepository.observarPase(ambito.alcance.servicio.id, ambito.turno.id).first()
            programacionVersion = pase?.programacionVersion
            val inicio = jornada.horario.substringBefore(" ").trim()
            val (h, m) = FormatoHora.separar(inicio) ?: (7 to 0)
            _uiState.update {
                it.copy(
                    cargando = false,
                    jornada = jornada,
                    contextoJornada = listOfNotNull(
                        pase?.fechaCorta,
                        "${ambito.turno.nombre} ${jornada.horario}",
                        ambito.alcance.servicio.nombre,
                    ).joinToString(" · "),
                    tipos = incidenciaRepository.tiposIncidencia(ambito.alcance.hospital.id),
                    desde = FormatoHora.horaDe(h, m),
                    hasta = FormatoHora.horaDe((h + 1) % 24, m),
                    puedeRegistrar = ambito.facultades.registrarIncidencia,
                )
            }
        }
    }

    fun onTipo(tipoId: String) = _uiState.update { estado ->
        val tipo = estado.tipos.firstOrNull { it.id == tipoId }
        estado.copy(
            tipoSeleccionadoId = tipoId,
            jornadaCompleta = if (tipo?.permiteParcial == false) true else estado.jornadaCompleta,
        )
    }

    fun onJornadaCompleta(completa: Boolean) = _uiState.update {
        if (!it.periodoEditable) it else it.copy(jornadaCompleta = completa)
    }

    fun onDesde(hora: String) = _uiState.update { it.copy(desde = hora) }

    fun onHasta(hora: String) = _uiState.update { it.copy(hasta = hora) }

    fun onObservacion(texto: String) = _uiState.update { it.copy(observacion = texto.take(MAX_OBSERVACION)) }

    fun onGuardar() {
        val estado = _uiState.value
        val jornada = estado.jornada ?: return
        val tipo = estado.tipoSeleccionado ?: return
        val ambito = scopeCache.ambitoActual() ?: return
        if (!estado.puedeGuardar) return
        _uiState.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val sesion = sessionStore.actual()
            if (sesion == null) {
                _uiState.update { it.copy(guardando = false) }
                return@launch
            }
            colaComandos.registrarIncidenciaPreliminar(
                contexto = ContextoCaptura(
                    actorId = sesion.usuarioId,
                    hospitalId = ambito.alcance.hospital.id,
                    servicioId = ambito.alcance.servicio.id,
                    programacionVersion = programacionVersion,
                ),
                jornada = jornada,
                tipo = tipo,
                jornadaCompleta = estado.jornadaCompleta,
                desde = estado.desde,
                hasta = estado.hasta,
                observacion = estado.observacion,
            )
            _eventos.send(RegistrarIncidenciaEvento.Guardada)
        }
    }

    private companion object {
        const val MAX_OBSERVACION = 200
    }
}
