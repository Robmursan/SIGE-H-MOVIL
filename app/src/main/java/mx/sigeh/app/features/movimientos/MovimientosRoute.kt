package mx.sigeh.app.features.movimientos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MovimientosRoute(
    onAbrirSincronizacion: () -> Unit,
    onRegistrarPaseSalida: () -> Unit,
    viewModel: MovimientosViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    MovimientosScreen(
        estado = estado,
        onAbrirSincronizacion = onAbrirSincronizacion,
        onFiltro = viewModel::onFiltro,
        onResponder = viewModel::onResponder,
        onRegistrarPaseSalida = onRegistrarPaseSalida,
        onMensajeMostrado = viewModel::onMensajeMostrado,
    )
}
