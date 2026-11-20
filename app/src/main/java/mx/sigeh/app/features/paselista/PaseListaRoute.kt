package mx.sigeh.app.features.paselista

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun PaseListaRoute(
    onCambiarServicio: () -> Unit,
    onAbrirDetalle: (String) -> Unit,
    onIniciarSesion: () -> Unit,
    viewModel: PaseListaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val alAbrirDetalle by rememberUpdatedState(onAbrirDetalle)

    // Al volver a primer plano se recarga: la capturabilidad depende de la hora del servidor.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onReanudar() }

    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                is PaseListaEvento.MostrarAcuse -> alcance.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar(evento.visuales) == SnackbarResult.ActionPerformed) {
                        alAbrirDetalle(evento.programacionId)
                    }
                }
            }
        }
    }

    PaseListaScreen(
        estado = estado,
        snackbarHostState = snackbar,
        acciones = AccionesPaseLista(
            onCambiarServicio = onCambiarServicio,
            onActualizar = viewModel::onActualizar,
            onDiaAnterior = viewModel::onDiaAnterior,
            onDiaSiguiente = viewModel::onDiaSiguiente,
            onHoy = viewModel::onHoy,
            onFechaSeleccionada = viewModel::onFechaSeleccionada,
            onTurnoSeleccionado = viewModel::onTurnoSeleccionado,
            onAbrirCaptura = viewModel::onAbrirCaptura,
            onAbrirDetalle = onAbrirDetalle,
            onCargarMas = viewModel::onCargarMas,
            onEstadoHoja = viewModel::onEstadoHoja,
            onConfirmar = viewModel::onConfirmar,
            onCancelarHoja = viewModel::onCancelarHoja,
            onReintentar = viewModel::onReintentar,
            onCerrarDialogo = viewModel::onCerrarDialogo,
            onIniciarSesion = onIniciarSesion,
        ),
    )
}
