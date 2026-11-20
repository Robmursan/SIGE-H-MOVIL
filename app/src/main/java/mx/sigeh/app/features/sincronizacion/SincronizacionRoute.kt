package mx.sigeh.app.features.sincronizacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SincronizacionRoute(
    onRegresar: () -> Unit,
    onResolver: (String) -> Unit,
    viewModel: SincronizacionViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    SincronizacionScreen(
        estado = estado,
        onRegresar = onRegresar,
        onReintentar = viewModel::onReintentar,
        onResolver = onResolver,
        onMensajeMostrado = viewModel::onMensajeMostrado,
    )
}
