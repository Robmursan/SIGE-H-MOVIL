package mx.sigeh.app.features.incidencias

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RegistrarIncidenciaRoute(
    onCerrar: () -> Unit,
    viewModel: RegistrarIncidenciaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alCerrar by rememberUpdatedState(onCerrar)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                RegistrarIncidenciaEvento.Guardada -> alCerrar()
            }
        }
    }
    RegistrarIncidenciaScreen(
        estado = estado,
        onCancelar = onCerrar,
        onTipo = viewModel::onTipo,
        onJornadaCompleta = viewModel::onJornadaCompleta,
        onDesde = viewModel::onDesde,
        onHasta = viewModel::onHasta,
        onObservacion = viewModel::onObservacion,
        onGuardar = viewModel::onGuardar,
    )
}
