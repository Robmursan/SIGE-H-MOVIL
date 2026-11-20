package mx.sigeh.app.core.network.lecturas

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lecturas de SIGE-H-WEB (`apps/api/src/modules/lecturas`), relativo a `/api/v1/`. Responden 503
 * «Lecturas aún no habilitadas» mientras el backend tenga `L3A_READS_ENABLED=false`.
 * Retrofit omite los parámetros `null`; quien llama nunca pasa `""`.
 */
interface LecturasApi {

    /**
     * operationId `listScopedWeeklyProgramming`. Permiso `PROGRAMACION_CONSULTAR`.
     * [desde] y [hasta] son date-time **con offset**; la ventana es de máximo 168 h (fin exclusivo).
     */
    @GET("programaciones")
    suspend fun programaciones(
        @Query("hospitalId") hospitalId: String,
        @Query("servicioId") servicioId: String,
        @Query("desde") desde: String,
        @Query("hasta") hasta: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): SchedulePageDto

    /** operationId `listScopedStaff`. Permiso `PERSONAL_CONSULTAR`. [fecha] es `YYYY-MM-DD`. */
    @GET("empleados")
    suspend fun empleados(
        @Query("hospitalId") hospitalId: String,
        @Query("servicioId") servicioId: String,
        @Query("fecha") fecha: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): EmployeePageDto

    companion object {
        const val LIMITE_PAGINA = 100

        /** Tope de páginas por consulta para no quedar en un bucle si el servidor repite cursores. */
        const val MAXIMO_PAGINAS = 20
    }
}
