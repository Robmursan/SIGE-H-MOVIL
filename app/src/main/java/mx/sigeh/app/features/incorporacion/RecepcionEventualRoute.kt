package mx.sigeh.app.features.incorporacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecepcionEventualRoute(
    onCerrar: () -> Unit,
    viewModel: RecepcionEventualViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alCerrar by rememberUpdatedState(onCerrar)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                RecepcionEvento.Terminada -> alCerrar()
            }
        }
    }
    RecepcionEventualScreen(
        estado = estado,
        onRegresar = onCerrar,
        onInicioEfectivo = viewModel::onInicioEfectivo,
        onConfirmar = viewModel::onConfirmar,
        onMostrarRechazo = viewModel::onMostrarRechazo,
        onMotivoRechazo = viewModel::onMotivoRechazo,
        onRechazar = viewModel::onRechazar,
    )
}
