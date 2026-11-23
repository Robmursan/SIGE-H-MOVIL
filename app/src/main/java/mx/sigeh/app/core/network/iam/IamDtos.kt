package mx.sigeh.app.core.network.iam

import kotlinx.serialization.Serializable

/*
 * DTO de IAM V2 tomados del contrato real de SIGE-H-WEB:
 * apps/api/src/modules/iam/presentation/auth.dto.ts y contexts.controller.ts
 * (contrato `artifacts/openapi/sigeh-v1.json`, revisión e2e-mvp-02.1, commit 9de9802, 2026-11-23,
 * SHA-256 a6fbe480…). Login, refresh, me, logout y sesiones no cambiaron respecto de 2604aa6.
 * Si el backend cambia esos DTO, actualizar aquí y en docs/mobile/contrato.md.
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
 * Elemento de `GET /auth/me/contextos` (`OwnContextDto`, operationId `iamOwnContexts`): un contexto
 * propio **vigente** de la sesión, con nombres legibles. Cada elemento es una asignación
 * ([assignmentId]); dos asignaciones del mismo ámbito llegan por separado.
 *
 * - A diferencia de `/auth/me`, los permisos llegan en `permisos` (no `permissions`) y el rol en `tipo`
 *   (no `kind`).
 * - `divisionId`, `divisionNombre`, `servicioId`, `servicioNombre` y `vigenteHasta` son obligatorios en el
 *   contrato pero admiten `null`: un contexto de hospital o de división no trae servicio. Llevan `= null`
 *   para aceptar tanto el `null` explícito como el campo ausente.
 * - `permisos` ya viene filtrado por el backend (un ENCARGADO solo trae lo encargado). Solo decide qué
 *   mostrar: el servidor vuelve a verificar facultad, sesión, vigencia y ámbito en cada operación.
 */
@Serializable
data class OwnContextDto(
    val assignmentId: String,
    val hospitalId: String,
    val hospitalNombre: String,
    /** Zona IANA del hospital: define la fecha local de `GET /pases-lista`. */
    val zonaHoraria: String,
    val divisionId: String? = null,
    val divisionNombre: String? = null,
    val servicioId: String? = null,
    val servicioNombre: String? = null,
    /** `TITULAR` o `ENCARGADO`. Solo se muestra: no otorga facultades. */
    val tipo: String,
    /** date-time ISO-8601. */
    val vigenteDesde: String,
    /** date-time ISO-8601; null si la asignación no tiene término (un ENCARGADO siempre trae uno). */
    val vigenteHasta: String? = null,
    val permisos: List<String> = emptyList(),
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
