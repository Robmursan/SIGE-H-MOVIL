package mx.sigeh.app.features.paselista

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
import mx.sigeh.app.core.data.CoberturaRepository
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.core.sync.ConnectivityObserver

/** Resumen del pase y cobertura en solo lectura (CU-ASI-06/08, COB). El balance se gestiona en la web. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResumenPaseViewModel @Inject constructor(
    scopeCache: ScopeCache,
    paseListaRepository: PaseListaRepository,
    coberturaRepository: CoberturaRepository,
    colaComandos: ColaComandos,
    conectividad: ConnectivityObserver,
) : ViewModel() {

    val uiState: StateFlow<ResumenPaseUiState> = scopeCache.activo
        .flatMapLatest { ambito ->
            if (ambito == null) return@flatMapLatest flowOf(ResumenPaseUiState(cargando = false))
            combine(
                paseListaRepository.observarPase(ambito.alcance.servicio.id, ambito.turno.id),
                colaComandos.observarTodos(),
                coberturaRepository.observarNecesidades(ambito.alcance.servicio.id),
                conectividad.enLinea,
            ) { pase, comandos, necesidades, enLinea ->
                val subtitulo = listOfNotNull(ambito.alcance.servicio.nombre, ambito.turno.nombre, pase?.fechaCorta)
                    .joinToString(" · ")
                if (pase == null) {
                    ResumenPaseUiState(cargando = false, subtitulo = subtitulo)
                } else {
                    construirResumen(subtitulo, pase, comandos, necesidades, enLinea)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenPaseUiState())
}
