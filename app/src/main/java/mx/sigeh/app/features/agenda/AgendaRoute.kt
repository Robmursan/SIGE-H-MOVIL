package mx.sigeh.app.features.agenda

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AgendaRoute(
    onAbrirSincronizacion: () -> Unit,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    AgendaScreen(estado = estado, onAbrirSincronizacion = onAbrirSincronizacion)
}
