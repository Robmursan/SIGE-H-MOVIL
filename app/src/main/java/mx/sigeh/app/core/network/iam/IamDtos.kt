package mx.sigeh.app.core.network.iam

import kotlinx.serialization.Serializable

/*
 * DTO de IAM V2 tomados del contrato real de SIGE-H-WEB:
 * apps/api/src/modules/iam/presentation/auth.dto.ts (rama main, commit 2604aa6, 2026-11-15).
 * Si el backend cambia esos DTO, actualizar aquí y en docs/mobile/contrato-pendiente.md.
 */

/** Cuerpo de `POST /auth/login`. `identificador`: 1–100 caracteres sin espacios en los extremos. */
@Serializable
data class LoginRequest(
    val identificador: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequest(identificador=$identificador, password=***)"
}

/** Cuerpo de `POST /auth/refresh`. Refresh opaco de exactamente 64 caracteres `[A-Za-z0-9_-]`. */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
) {
    override fun toString(): String = "RefreshRequest(refreshToken=***)"
}

/** Respuesta de login y refresh (`TokensDto`). `expiresIn` en segundos (300 por defecto). */
@Serializable
data class TokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val sessionId: String,
) {
    override fun toString(): String = "TokensResponse(tokenType=$tokenType, expiresIn=$expiresIn, sessionId=$sessionId)"
}

/** Respuesta de `GET /auth/me` (`ProfileDto`): perfil y facultades explícitas vigentes. */
@Serializable
data class ProfileResponse(
    val usuarioId: String,
    val sessionId: String,
    val provider: String,
    val assignments: List<AssignmentDto> = emptyList(),
)

@Serializable
data class AssignmentDto(
    /** "TITULAR", "ENCARGADO" o null. */
    val kind: String? = null,
    val scope: ScopeDto,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class ScopeDto(
    val hospitalId: String,
    val divisionId: String? = null,
    val servicioId: String? = null,
)

/**
 * Elemento de `GET /auth/sessions` (`SessionDto`, contrato `sigeh-v1`). Fechas ISO-8601.
 * [current] marca la sesión con la que se hizo la petición.
 */
@Serializable
data class SessionDto(
    val sessionId: String,
    val createdAt: String,
    val expiresAt: String,
    val idleExpiresAt: String,
    val revokedAt: String? = null,
    val current: Boolean,
)

/** Respuesta de `GET /meta/version` (CU-OPS-03). No acredita compatibilidad de clientes ni readiness. */
@Serializable
data class ApiVersionResponse(
    val apiVersion: String,
    val apiBasePath: String,
)
