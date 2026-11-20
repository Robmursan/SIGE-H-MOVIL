package mx.sigeh.app.features.login

/** Estado del servidor en el pie del login (`GET /meta/version`). */
sealed interface EstadoServidor {
    /** Modo Fake: no hay servidor. */
    data object Demostracion : EstadoServidor

    data object Consultando : EstadoServidor

    /** «Servidor en línea · contrato API 1.0.0». */
    data class EnLinea(val version: String) : EstadoServidor

    data object SinRespuesta : EstadoServidor

    /** `apiVersion` no empieza con `1.`: se bloquea el login con «Actualiza la aplicación». */
    data class Incompatible(val version: String) : EstadoServidor
}

data class LoginUiState(
    val identificador: String = "",
    val contrasena: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val servidor: EstadoServidor = EstadoServidor.Demostracion,
    /** Segundos que faltan tras un 429; el botón queda desactivado hasta llegar a cero. */
    val esperaSegundos: Long = 0,
) {
    val puedeEnviar: Boolean
        get() = identificador.isNotBlank() && contrasena.isNotBlank() && !cargando && esperaSegundos <= 0 &&
            servidor !is EstadoServidor.Incompatible
}

/** Límite del contrato para `identificador` (`LoginDto`). */
const val LONGITUD_MAXIMA_IDENTIFICADOR = 100

sealed interface LoginEvento {
    data object SesionIniciada : LoginEvento
}
