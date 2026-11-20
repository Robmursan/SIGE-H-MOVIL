package mx.sigeh.app.features.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import mx.sigeh.app.core.designsystem.components.formatoCuentaRegresiva
import mx.sigeh.app.core.sync.ConnectivityObserver

/** Pantalla 9.1 · Iniciar sesión (IAM V2 real o Fake). */
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

    private var cuentaRegresiva: Job? = null

    init {
        if (servidorRepository.modoReal) consultarServidor()
    }

    /** `GET /meta/version`: el contrato debe ser `1.x`; si no, la app no se puede usar. */
    fun consultarServidor() {
        if (!servidorRepository.modoReal) return
        _uiState.update { it.copy(servidor = EstadoServidor.Consultando) }
        viewModelScope.launch {
            val version = servidorRepository.versionContrato()
            val estado = when {
                version == null -> EstadoServidor.SinRespuesta
                version.startsWith(VERSION_COMPATIBLE) -> EstadoServidor.EnLinea(version)
                else -> EstadoServidor.Incompatible(version)
            }
            _uiState.update { it.copy(servidor = estado) }
        }
    }

    fun onIdentificadorChange(valor: String) =
        _uiState.update { it.copy(identificador = valor.take(LONGITUD_MAXIMA_IDENTIFICADOR), error = null) }

    fun onContrasenaChange(valor: String) = _uiState.update { it.copy(contrasena = valor, error = null) }

    fun iniciarSesion() {
        val estado = _uiState.value
        if (!estado.puedeEnviar) return
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = sesionManager.iniciarSesion(estado.identificador.trim(), estado.contrasena)
            when (resultado) {
                is ResultadoLogin.Exito -> {
                    _uiState.update { it.copy(identificador = "", contrasena = "", cargando = false, error = null) }
                    _eventos.send(LoginEvento.SesionIniciada)
                }
                is ResultadoLogin.LimiteIntentos -> {
                    _uiState.update { it.copy(cargando = false) }
                    esperar(resultado.esperaSegundos)
                }
                else -> _uiState.update { it.copy(cargando = false, error = mensajeDe(resultado)) }
            }
        }
    }

    /** 429: cuenta regresiva de `Retry-After`; al terminar se puede volver a intentar. */
    private fun esperar(segundos: Long) {
        cuentaRegresiva?.cancel()
        cuentaRegresiva = viewModelScope.launch {
            var restantes = segundos
            while (restantes > 0) {
                _uiState.update { it.copy(esperaSegundos = restantes, error = textoEspera(restantes)) }
                delay(1_000)
                restantes--
            }
            _uiState.update { it.copy(esperaSegundos = 0, error = null) }
        }
    }

    private fun mensajeDe(resultado: ResultadoLogin): String = when (resultado) {
        is ResultadoLogin.Exito, is ResultadoLogin.LimiteIntentos -> ""
        // Genérico a propósito: no revela si la cuenta existe (FC-01).
        ResultadoLogin.CredencialesInvalidas -> "Identificador o contraseña incorrectos."
        ResultadoLogin.ServicioNoDisponible -> "El inicio de sesión no está disponible por ahora."
        ResultadoLogin.SinConexion ->
            if (conectividad.enLinea.value) {
                "No se pudo contactar al servidor de SIGE-H. Intenta más tarde."
            } else {
                "Sin conexión. Para iniciar sesión necesitas red."
            }
    }

    companion object {
        /** Versión mayor del contrato que entiende esta app (`sigeh-v1`). */
        const val VERSION_COMPATIBLE = "1."

        fun textoEspera(segundos: Long) =
            "Demasiados intentos. Podrás volver a intentar en ${formatoCuentaRegresiva(segundos)}."
    }
}
