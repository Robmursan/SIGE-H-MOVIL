package mx.sigeh.app.features.incidencias

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun IncidenciasRoute(
    onAbrirSincronizacion: () -> Unit,
    onRegistrar: (programacionId: String) -> Unit,
    viewModel: IncidenciasViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    IncidenciasScreen(estado = estado, onAbrirSincronizacion = onAbrirSincronizacion, onRegistrar = onRegistrar)
}
