package mx.sigeh.app.features.sincronizacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConflictoRoute(
    onRegresar: () -> Unit,
    onResuelto: () -> Unit,
    viewModel: ConflictoViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alResolver by rememberUpdatedState(onResuelto)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                ConflictoEvento.Resuelto -> alResolver()
            }
        }
    }
    ConflictoScreen(
        estado = estado,
        onRegresar = onRegresar,
        onOpcion = viewModel::onOpcion,
        onMotivoChange = viewModel::onMotivoChange,
        onConfirmar = viewModel::onConfirmar,
    )
}
