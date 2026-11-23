package mx.sigeh.app.core.network.iam

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * IAM V2 de SIGE-H-WEB (`apps/api/src/modules/iam/presentation/auth.controller.ts`), relativo a `/api/v1/`.
 * Mientras el backend tenga `IAM_V2_ENABLED=false`, todas responden 503 "IAM no disponible".
 * Las sesiones (`/auth/sessions`) se integran en M2 para la pantalla de Cuenta.
 */
interface IamApi {

    /** operationId `iamLogin`: 200 tokens · 400 entrada · 401 genérico · 429 límite · 503 IAM deshabilitado. */
    @POST("auth/login")
    suspend fun login(@Body cuerpo: LoginRequest): TokensResponse

    /**
     * operationId `iamRefresh`: rota el refresh (un solo uso; un replay revoca la familia).
     * Es síncrona a propósito: la usa el `Authenticator` de OkHttp desde su propio hilo.
     */
    @POST("auth/refresh")
    fun refrescar(@Body cuerpo: RefreshRequest): Call<TokensResponse>

    /**
     * operationId `iamMe`. [autorizacion] solo se pasa justo después del login, antes de guardar la
     * sesión; en el resto de los casos es null y el `AuthInterceptor` agrega el token vigente.
     */
    @GET("auth/me")
    suspend fun perfil(@Header("Authorization") autorizacion: String?): ProfileResponse

    /**
     * operationId `iamOwnContexts` (nuevo en el contrato e2e-mvp-02.1): contextos propios vigentes con
     * nombres de hospital, división y servicio, y la zona horaria del hospital. Solo pide sesión: no
     * requiere `CATALOGOS_ADMINISTRAR` ni enumera servicios hijos de una división.
     *
     * No admite parámetros: cualquier query responde 400 «Contextos propios no admite filtros».
     * 401 · 503 «Contextos no disponibles» (persistencia o permisos SQL) · 503 «IAM no disponible».
     * El contrato la marca `prepared-own-contexts-awaiting-sql-validation`: quien la usa debe tener
     * un respaldo (las asignaciones de `/auth/me`).
     */
    @GET("auth/me/contextos")
    suspend fun contextos(): List<OwnContextDto>

    /** operationId `iamLogout`: 204, revoca la sesión actual en el servidor. */
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    /** operationId `iamSessions`: últimas 50 sesiones propias, sin tokens. 401 · 503. */
    @GET("auth/sessions")
    suspend fun sesiones(): List<SessionDto>

    /**
     * operationId `iamRevokeSession`: 204. 404 si la sesión no es del actor. Revocar la sesión
     * `current` equivale a cerrar sesión: quien llama purga todo lo local.
     */
    @DELETE("auth/sessions/{sessionId}")
    suspend fun revocarSesion(@Path("sessionId") sessionId: String): Response<Unit>
}

/** Operaciones (`apps/api/src/modules/operaciones/operaciones.controller.ts`). Sin sesión ni base de datos. */
interface OperacionesApi {

    /** operationId `getApiContractVersion` (CU-OPS-03). */
    @GET("meta/version")
    suspend fun version(): ApiVersionResponse
}
