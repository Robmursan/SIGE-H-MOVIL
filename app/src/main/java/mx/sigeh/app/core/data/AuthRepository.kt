package mx.sigeh.app.core.data

import mx.sigeh.app.core.auth.Session

/** IAM: CU-IAM-01 (login), CU-IAM-03 (logout). El inicio de sesión requiere conexión. */
interface AuthRepository {
    suspend fun iniciarSesion(usuario: String, contrasena: String): ResultadoLogin

    /** Revoca la sesión en el servidor si es posible; nunca impide la purga local. */
    suspend fun cerrarSesion()
}

sealed interface ResultadoLogin {
    data class Exito(val sesion: Session) : ResultadoLogin

    /** 400/401 genérico: no revela si la cuenta existe (FC-01). */
    data object CredencialesInvalidas : ResultadoLogin

    /**
     * 429: límite persistente de intentos por cuenta o por red. [esperaSegundos] sale de
     * `Retry-After` (60 s si no viene) para la cuenta regresiva del botón.
     */
    data class LimiteIntentos(val esperaSegundos: Long) : ResultadoLogin

    /** 503 (IAM deshabilitado o persistencia caída) u otra respuesta inesperada del servidor. */
    data object ServicioNoDisponible : ResultadoLogin

    data object SinConexion : ResultadoLogin
}
