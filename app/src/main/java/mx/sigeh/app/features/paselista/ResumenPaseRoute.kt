package mx.sigeh.app.features.paselista

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ResumenPaseRoute(
    onRegresar: () -> Unit,
    onAbrirConflicto: (String) -> Unit,
    onAbrirRecepcion: (String) -> Unit,
    viewModel: ResumenPaseViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    ResumenPaseScreen(
        estado = estado,
        onRegresar = onRegresar,
        onAbrirConflicto = onAbrirConflicto,
        onVerSinCapturar = onRegresar,
        onAbrirRecepcion = onAbrirRecepcion,
    )
}
