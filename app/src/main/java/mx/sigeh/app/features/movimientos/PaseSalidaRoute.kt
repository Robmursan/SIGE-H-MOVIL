package mx.sigeh.app.features.movimientos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PaseSalidaRoute(
    onCerrar: () -> Unit,
    viewModel: PaseSalidaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alCerrar by rememberUpdatedState(onCerrar)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                PaseSalidaEvento.Registrada -> alCerrar()
            }
        }
    }
    PaseSalidaScreen(
        estado = estado,
        onCancelar = onCerrar,
        onTrabajador = viewModel::onTrabajador,
        onHoraSalida = viewModel::onHoraSalida,
        onAhora = viewModel::onAhora,
        onRegistrarRegreso = viewModel::onRegistrarRegreso,
        onHoraRegreso = viewModel::onHoraRegreso,
        onNota = viewModel::onNota,
        onGuardar = viewModel::onGuardar,
    )
}
