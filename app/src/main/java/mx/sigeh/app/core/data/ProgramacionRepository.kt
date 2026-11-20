package mx.sigeh.app.core.data

import mx.sigeh.app.core.model.JornadaSemanal
import mx.sigeh.app.core.network.ApiError
import mx.sigeh.app.core.network.CausaErrorApi

/**
 * Programación semanal del servicio en línea (M2 §9.8, `GET /programaciones`). Solo se ofrece con
 * `PROGRAMACION_CONSULTAR`; los nombres, solo con `PERSONAL_CONSULTAR` (`GET /empleados`).
 */
interface ProgramacionRepository {

    /**
     * Todas las páginas de la ventana [desde]–[hasta] (date-time con offset, máximo 168 h).
     *
     * @param conNombres si la sesión tiene `PERSONAL_CONSULTAR` en el servicio. Si el padrón falla,
     *   la programación se devuelve igual, sin nombres.
     * @param fechaPadron fecha local (`YYYY-MM-DD`) del padrón vigente.
     */
    suspend fun semana(
        hospitalId: String,
        servicioId: String,
        desde: String,
        hasta: String,
        conNombres: Boolean,
        fechaPadron: String,
    ): ResultadoProgramacion
}

sealed interface ResultadoProgramacion {
    /** [nombresDisponibles] en false: se muestra «Trabajador ··· abcd». */
    data class Exito(val jornadas: List<JornadaSemanal>, val nombresDisponibles: Boolean) : ResultadoProgramacion

    /** 403: el ámbito no tiene `PROGRAMACION_CONSULTAR`. */
    data object SinPermiso : ResultadoProgramacion

    /** 503 «Lecturas aún no habilitadas». */
    data object NoHabilitada : ResultadoProgramacion

    data object SesionTerminada : ResultadoProgramacion

    /** Red, 400, 503 transitorio u otra respuesta inesperada. */
    data object Fallida : ResultadoProgramacion
}

fun ApiError.aResultadoProgramacion(): ResultadoProgramacion = when (httpCode) {
    401 -> ResultadoProgramacion.SesionTerminada
    403 -> ResultadoProgramacion.SinPermiso
    503 -> if (causa == CausaErrorApi.LECTURAS_NO_HABILITADAS) ResultadoProgramacion.NoHabilitada else ResultadoProgramacion.Fallida
    else -> ResultadoProgramacion.Fallida
}
