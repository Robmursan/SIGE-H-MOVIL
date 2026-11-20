package mx.sigeh.app.features.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginRoute(
    sesionVencida: Boolean,
    onSesionIniciada: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alIniciar by rememberUpdatedState(onSesionIniciada)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                LoginEvento.SesionIniciada -> alIniciar()
            }
        }
    }
    LoginScreen(
        estado = estado,
        sesionVencida = sesionVencida,
        onUsuarioChange = viewModel::onUsuarioChange,
        onContrasenaChange = viewModel::onContrasenaChange,
        onIniciarSesion = viewModel::iniciarSesion,
    )
}
