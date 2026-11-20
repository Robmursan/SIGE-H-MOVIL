package mx.sigeh.app.features.incidencias

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
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.core.sync.ConnectivityObserver
import mx.sigeh.app.features.sincronizacion.describirRegistro

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IncidenciasViewModel @Inject constructor(
    scopeCache: ScopeCache,
    paseListaRepository: PaseListaRepository,
    colaComandos: ColaComandos,
    conectividad: ConnectivityObserver,
) : ViewModel() {

    val uiState: StateFlow<IncidenciasUiState> = scopeCache.activo
        .flatMapLatest { ambito ->
            if (ambito == null) return@flatMapLatest flowOf(IncidenciasUiState(cargando = false, sinProgramacion = true))
            combine(
                paseListaRepository.observarPase(ambito.alcance.servicio.id, ambito.turno.id),
                colaComandos.observarTodos(),
                conectividad.enLinea,
            ) { pase, comandos, enLinea ->
                val ids = pase?.jornadas?.map { it.programacionId }?.toSet().orEmpty()
                IncidenciasUiState(
                    cargando = false,
                    sinProgramacion = pase == null,
                    subtitulo = listOfNotNull(ambito.alcance.servicio.nombre, ambito.turno.nombre, pase?.fechaCorta)
                        .joinToString(" · "),
                    porEnviar = comandos.count { it.estado.porEnviar },
                    registradas = comandos
                        .filter { it.tipo == CommandType.INCIDENCIA_PRELIMINAR && it.programacionId in ids }
                        .sortedByDescending { it.creadoEnMillis }
                        .map { describirRegistro(it, enLinea, aplicadoEnMillis = null, servicioAjeno = null) },
                    jornadas = pase?.jornadas.orEmpty().filter { it.exigible }.map {
                        JornadaParaIncidencia(it.programacionId, it.nombre, "${it.categoria} · RUD ···${it.rudUltimos4}")
                    },
                    puedeRegistrar = ambito.facultades.registrarIncidencia,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidenciasUiState())
}
