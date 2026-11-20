package mx.sigeh.app.features.paselista

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PaseListaRoute(
    onRegresar: () -> Unit,
    onAbrirSincronizacion: () -> Unit,
    onAbrirConflicto: (String) -> Unit,
    onAbrirResumen: () -> Unit,
    viewModel: PaseListaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alAbrirResumen by rememberUpdatedState(onAbrirResumen)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                PaseListaEvento.AbrirResumen -> alAbrirResumen()
            }
        }
    }
    PaseListaScreen(
        estado = estado,
        onRegresar = onRegresar,
        onAbrirSincronizacion = onAbrirSincronizacion,
        onEstadoSeleccionado = viewModel::onEstadoSeleccionado,
        onAbrirConflicto = onAbrirConflicto,
        onGuardarBorrador = viewModel::onGuardarBorrador,
        onEnviarAValidar = viewModel::onEnviarAValidar,
        onMensajeMostrado = viewModel::onMensajeMostrado,
    )
}
