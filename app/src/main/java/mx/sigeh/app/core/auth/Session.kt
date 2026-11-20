package mx.sigeh.app.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import mx.sigeh.app.core.model.RolAlcance

/**
 * Asignación vigente que devuelve `GET /auth/me` (IAM V2): rol, ámbito por UUID y permisos.
 * El backend aún no expone nombres ni vigencias de organización (CU-ORG-01/04/07 pendientes).
 */
@Serializable
data class AsignacionSesion(
    val rol: RolAlcance? = null,
    val hospitalId: String,
    val divisionId: String? = null,
    val servicioId: String? = null,
    val permisos: List<String> = emptyList(),
)

/**
 * Sesión del dispositivo. Se guarda solo cifrada (ver [EncryptedSessionStore]).
 * `vencida` se activa ante 401/403 o un refresh rechazado: detiene la cola hasta volver a iniciar sesión.
 *
 * Los campos de IAM real son opcionales para seguir leyendo sesiones guardadas por M0 (modo Fake).
 */
@Serializable
data class Session(
    val usuarioId: String,
    val nombreCorto: String,
    val accessToken: String,
    val vencida: Boolean = false,
    /** Refresh opaco de un solo uso (64 caracteres). Reutilizarlo revoca toda la familia de sesiones. */
    val refreshToken: String? = null,
    val sessionId: String? = null,
    /** Instante local en que vence el access token (expiresIn = 300 s en IAM V2). */
    val accessExpiraEnMillis: Long? = null,
    /** Asignaciones reales de `GET /auth/me`; null en modo Fake (el servidor no informó nada). */
    val asignaciones: List<AsignacionSesion>? = null,
) {
    // Nunca exponer tokens en logs ni en volcados de estado.
    override fun toString(): String = "Session(usuarioId=$usuarioId, vencida=$vencida, sessionId=$sessionId)"
}

interface SessionStore {
    val session: Flow<Session?>

    suspend fun actual(): Session?

    /** Lectura sin suspender para los interceptores de OkHttp (corren en hilos de red). */
    fun sesionEnMemoria(): Session?

    fun tokenEnMemoria(): String? = sesionEnMemoria()?.takeUnless { it.vencida }?.accessToken

    suspend fun guardar(session: Session)

    suspend fun marcarVencida()

    suspend fun limpiar()
}

/** Estado global de la sesión que observa la navegación. */
enum class EstadoSesion { CARGANDO, SIN_SESION, ACTIVA, VENCIDA }
