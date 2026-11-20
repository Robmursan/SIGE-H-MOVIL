package mx.sigeh.app.features.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.data.ProgramacionRepository
import mx.sigeh.app.core.sync.ColaComandos

/** Agenda propia (CU-CAL-19), leída de la copia guardada en el teléfono. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaViewModel @Inject constructor(
    scopeCache: ScopeCache,
    programacionRepository: ProgramacionRepository,
    sessionStore: SessionStore,
    colaComandos: ColaComandos,
) : ViewModel() {

    val uiState: StateFlow<AgendaUiState> = scopeCache.activo
        .flatMapLatest { ambito ->
            if (ambito == null) return@flatMapLatest flowOf(AgendaUiState(cargando = false))
            combine(
                programacionRepository.observarAgenda(ambito.alcance.servicio.id, ambito.turno.id),
                sessionStore.session,
                colaComandos.observarPorEnviar(),
            ) { agenda, sesion, porEnviar ->
                AgendaUiState(
                    cargando = false,
                    subtitulo = listOfNotNull(sesion?.nombreCorto, ambito.alcance.servicio.nombre).joinToString(" · "),
                    agenda = agenda,
                    porEnviar = porEnviar,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())
}
