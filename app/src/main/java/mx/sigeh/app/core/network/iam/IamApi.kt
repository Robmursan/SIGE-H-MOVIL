package mx.sigeh.app.core.network.iam

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * IAM V2 de SIGE-H-WEB (`apps/api/src/modules/iam/presentation/auth.controller.ts`), relativo a `/api/v1/`.
 * Mientras el backend tenga `IAM_V2_ENABLED=false`, todas responden 503 "IAM no disponible".
 *
 * No se integran `GET /auth/sessions` ni `DELETE /auth/sessions/:id`: no hay pantalla para ellas en M1.
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

    /** operationId `iamLogout`: 204, revoca la sesión actual en el servidor. */
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>
}

/** Operaciones (`apps/api/src/modules/operaciones/operaciones.controller.ts`). Sin sesión ni base de datos. */
interface OperacionesApi {

    /** operationId `getApiContractVersion` (CU-OPS-03). */
    @GET("meta/version")
    suspend fun version(): ApiVersionResponse
}
