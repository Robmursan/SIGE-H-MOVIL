package mx.sigeh.app.core.data

import java.util.concurrent.ConcurrentHashMap
import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.PaginaPaseLista
import mx.sigeh.app.core.network.ApiError
import mx.sigeh.app.core.network.CausaErrorApi

/**
 * Pase de lista **en línea** (M2, CU-ASI-01/02) con el modelo del servidor (contrato `sigeh-v1`).
 *
 * - Solo en línea: nada pasa por `ColaComandos` ni por WorkManager.
 * - Primera captura únicamente (`expectedRevision = "0"`); no hay corrección desde la app.
 * - El servidor decide la capturabilidad; el `PUT` es la autoridad final.
 *
 * Implementaciones: `RemotoPaseListaRepository` (`sigeh.api.asistencia=real`) y `FakePaseListaRepository`.
 */
interface PaseListaRepository {

    /**
     * `GET /pases-lista` con `limit=100`. Sin [cursor] es la primera página y reemplaza la caché;
     * con [cursor] («Cargar más», mismos filtros) se agrega a ella. Los opcionales vacíos no se envían.
     *
     * @param fecha fecha local del hospital, `YYYY-MM-DD`.
     */
    suspend fun consultar(
        hospitalId: String,
        servicioId: String,
        fecha: String,
        horarioId: String? = null,
        cursor: String? = null,
    ): ResultadoConsulta

    /**
     * `PUT /programaciones/{programacionId}/asistencia`. [idempotencyKey] la genera quien llama una
     * sola vez por intención (UUID v4) y la reutiliza tal cual mientras el resultado no sea terminal.
     */
    suspend fun capturar(programacionId: String, estado: EstadoCaptura, idempotencyKey: String): ResultadoCaptura

    /** Jornada de las páginas ya consultadas (para `DetalleJornada`, que no llama a otro endpoint). */
    fun jornada(programacionId: String): JornadaPase?

    /** Último acuse recibido para esa jornada en esta sesión («Ver acuse»). */
    fun acuse(programacionId: String): AcuseCaptura?

    /** Purga la caché en memoria (cierre de sesión o cambio de persona). */
    fun limpiar()
}

/** Resultado de `GET /pases-lista` (prompt M2 §6.6). */
sealed interface ResultadoConsulta {
    /** 200. Una lista vacía también es éxito («No hay jornadas programadas para este día»). */
    data class Exito(val pagina: PaginaPaseLista) : ResultadoConsulta

    /** 403 «Acceso denegado»: «Sin permiso en este servicio», con acción «Elegir servicio». */
    data object SinPermiso : ResultadoConsulta

    /** 404 «Contexto no disponible»: el servicio ya no está disponible para esta sesión. */
    data object ContextoNoDisponible : ResultadoConsulta

    /** 503 «Asistencia no habilitada»: «Pase de lista no disponible en este servidor», sin reintento automático. */
    data object NoHabilitada : ResultadoConsulta

    /** 401 después de intentar una renovación. */
    data object SesionTerminada : ResultadoConsulta

    /** Red, timeout, 503 transitorio, 5xx o 400 (error de la app, solo se registra en debug): se ofrece reintentar. */
    data object Fallida : ResultadoConsulta
}

/** Traduce un error HTTP de la consulta según la tabla del prompt M2 §6.6. */
fun ApiError.aResultadoConsulta(): ResultadoConsulta = when (httpCode) {
    401 -> ResultadoConsulta.SesionTerminada
    403 -> ResultadoConsulta.SinPermiso
    404 -> ResultadoConsulta.ContextoNoDisponible
    503 -> if (causa == CausaErrorApi.ASISTENCIA_NO_HABILITADA) ResultadoConsulta.NoHabilitada else ResultadoConsulta.Fallida
    else -> ResultadoConsulta.Fallida
}

/**
 * Caché en memoria compartida por las implementaciones de [PaseListaRepository]. Nunca se persiste:
 * contiene nombres de personas y se purga al cerrar sesión.
 */
class CachePaseLista {
    private val jornadas = ConcurrentHashMap<String, JornadaPase>()
    private val acuses = ConcurrentHashMap<String, AcuseCaptura>()

    /** La primera página reemplaza lo anterior (otro servicio, fecha u horario); las siguientes se agregan. */
    fun registrarPagina(pagina: PaginaPaseLista, primeraPagina: Boolean) {
        if (primeraPagina) jornadas.clear()
        pagina.jornadas.forEach { jornadas[it.programacionId] = it }
    }

    fun registrarAcuse(acuse: AcuseCaptura) {
        acuses[acuse.programacionId] = acuse
    }

    fun jornada(programacionId: String): JornadaPase? = jornadas[programacionId]

    fun acuse(programacionId: String): AcuseCaptura? = acuses[programacionId]

    fun limpiar() {
        jornadas.clear()
        acuses.clear()
    }
}
