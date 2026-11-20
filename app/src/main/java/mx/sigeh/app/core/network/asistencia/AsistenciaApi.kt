package mx.sigeh.app.core.network.asistencia

import mx.sigeh.app.core.network.IdempotencyKeyInterceptor
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Asistencia operativa de SIGE-H-WEB (`apps/api/src/modules/asistencia/asistencia.controller.ts`),
 * relativo a `/api/v1/`. Ambas rutas responden 503 «Asistencia no habilitada» mientras el backend
 * tenga `ASISTENCIA_ENABLED=false`.
 *
 * Solo en línea: la captura nunca pasa por `ColaComandos` ni por WorkManager.
 */
interface AsistenciaApi {

    /**
     * operationId `listScopedAttendanceRoster`. Permiso `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`.
     *
     * Retrofit omite los parámetros `null`. El backend responde 400 ante parámetros vacíos, nulos o
     * repetidos, así que quien llama pasa `null` (nunca `""`) en los opcionales.
     *
     * @param fecha fecha local del hospital, `YYYY-MM-DD`.
     * @param limit 1–100; la app pide [LIMITE_PAGINA].
     */
    @GET("pases-lista")
    suspend fun pasesLista(
        @Query("hospitalId") hospitalId: String,
        @Query("servicioId") servicioId: String,
        @Query("fecha") fecha: String,
        @Query("horarioId") horarioId: String?,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): PaseListaPageDto

    /**
     * operationId `captureInitialAttendance`. Permiso `ASISTENCIA_CAPTURAR`.
     *
     * [idempotencyKey] es un UUID v4 generado una sola vez al confirmar. Se reutiliza tal cual en los
     * reintentos de la misma intención (timeout, red, 503, 401 con refresh): el servidor devuelve
     * el mismo acuse. Con otro estado la misma clave da 409 `IDEMPOTENCY_CONFLICT`.
     */
    @PUT("programaciones/{programacionId}/asistencia")
    suspend fun capturar(
        @Path("programacionId") programacionId: String,
        @Header(IdempotencyKeyInterceptor.ENCABEZADO) idempotencyKey: String,
        @Body cuerpo: CapturarAsistenciaDto,
    ): CaptureReceiptDto

    companion object {
        /** Máximo del contrato; la app siempre pide páginas completas. */
        const val LIMITE_PAGINA = 100
    }
}
