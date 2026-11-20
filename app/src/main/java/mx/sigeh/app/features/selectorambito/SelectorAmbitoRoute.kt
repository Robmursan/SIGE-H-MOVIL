package mx.sigeh.app.features.selectorambito

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SelectorAmbitoRoute(
    alcanceVencido: Boolean,
    onContinuar: () -> Unit,
    onSesionCerrada: () -> Unit,
    viewModel: SelectorAmbitoViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alContinuar by rememberUpdatedState(onContinuar)
    val alCerrar by rememberUpdatedState(onSesionCerrada)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                SelectorAmbitoEvento.Continuar -> alContinuar()
                SelectorAmbitoEvento.SesionCerrada -> alCerrar()
            }
        }
    }
    SelectorAmbitoScreen(
        estado = estado,
        alcanceVencido = alcanceVencido,
        onSeleccionar = viewModel::onSeleccionar,
        onContinuar = viewModel::onContinuar,
        onCerrarSesion = viewModel::onCerrarSesion,
    )
}
