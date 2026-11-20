package mx.sigeh.app.features.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.data.ServidorRepository
import mx.sigeh.app.core.sync.ConnectivityObserver

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sesionManager: SesionManager,
    private val servidorRepository: ServidorRepository,
    private val conectividad: ConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _eventos = Channel<LoginEvento>(Channel.BUFFERED)
    val eventos: Flow<LoginEvento> = _eventos.receiveAsFlow()

    init {
        if (servidorRepository.modoReal) {
            viewModelScope.launch {
                val version = servidorRepository.versionContrato()
                _uiState.update {
                    it.copy(
                        pieServidor = version?.let { v -> "Servidor SIGE-H · contrato API $v" }
                            ?: "Servidor SIGE-H sin respuesta",
                    )
                }
            }
        }
    }

    fun onUsuarioChange(valor: String) = _uiState.update { it.copy(usuario = valor, error = null) }

    fun onContrasenaChange(valor: String) = _uiState.update { it.copy(contrasena = valor, error = null) }

    fun iniciarSesion() {
        val estado = _uiState.value
        if (!estado.puedeEnviar) return
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = sesionManager.iniciarSesion(estado.usuario.trim(), estado.contrasena)
            if (resultado is ResultadoLogin.Exito) {
                _uiState.update { it.copy(usuario = "", contrasena = "", cargando = false, error = null) }
                _eventos.send(LoginEvento.SesionIniciada)
                return@launch
            }
            _uiState.update { it.copy(cargando = false, error = mensajeDe(resultado)) }
        }
    }

    private fun mensajeDe(resultado: ResultadoLogin): String = when (resultado) {
        is ResultadoLogin.Exito -> ""
        // Genérico a propósito: no revela si la cuenta existe (FC-01).
        ResultadoLogin.CredencialesInvalidas -> "Usuario o contraseña incorrectos."
        ResultadoLogin.LimiteIntentos ->
            "Demasiados intentos. Espera unos minutos antes de volver a intentarlo."
        ResultadoLogin.ServicioNoDisponible ->
            "El inicio de sesión no está disponible por ahora. Intenta más tarde o acude con Coordinación de enfermería."
        ResultadoLogin.SinConexion ->
            if (conectividad.enLinea.value) {
                "No se pudo contactar al servidor de SIGE-H. Intenta más tarde."
            } else {
                "Sin conexión. Para iniciar sesión necesitas red."
            }
    }
}
