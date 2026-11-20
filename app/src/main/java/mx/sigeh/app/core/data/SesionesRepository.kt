package mx.sigeh.app.core.data

/**
 * Sesiones de IAM V2 de la persona (pantalla de Cuenta): `GET /auth/sessions` y
 * `DELETE /auth/sessions/{sessionId}`. Ninguna respuesta lleva tokens.
 */
interface SesionesRepository {
    suspend fun sesiones(): ResultadoSesiones

    /**
     * Revoca una sesión propia. Si era la de este dispositivo ([ResultadoRevocacion.Revocada.eraLaActual]),
     * quien llama lo trata como un cierre de sesión y purga todo lo local.
     */
    suspend fun revocar(sessionId: String): ResultadoRevocacion
}

/**
 * Sesión de IAM (`SessionDto`). Las fechas son ISO-8601; se formatean en la pantalla.
 * [actual] es la sesión con la que se consultó.
 */
data class SesionIam(
    val sessionId: String,
    val iniciadaEn: String,
    val expiraEn: String,
    val inactividadExpiraEn: String,
    val revocadaEn: String?,
    val actual: Boolean,
) {
    val revocada: Boolean get() = revocadaEn != null
}

sealed interface ResultadoSesiones {
    /** Últimas 50 sesiones propias, en el orden del servidor. */
    data class Exito(val sesiones: List<SesionIam>) : ResultadoSesiones

    data object SesionTerminada : ResultadoSesiones

    /** Red, 503 u otra respuesta inesperada. */
    data object Fallida : ResultadoSesiones
}

sealed interface ResultadoRevocacion {
    /** 204. Con [eraLaActual] la sesión de este dispositivo quedó revocada: hay que purgar y volver al login. */
    data class Revocada(val eraLaActual: Boolean) : ResultadoRevocacion

    /** 404: la sesión no existe o no es de esta persona. */
    data object NoEncontrada : ResultadoRevocacion

    data object SesionTerminada : ResultadoRevocacion

    data object Fallida : ResultadoRevocacion
}
