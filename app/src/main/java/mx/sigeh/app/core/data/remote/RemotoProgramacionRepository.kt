package mx.sigeh.app.core.data.remote

import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import mx.sigeh.app.core.data.ProgramacionRepository
import mx.sigeh.app.core.data.ResultadoProgramacion
import mx.sigeh.app.core.data.aResultadoProgramacion
import mx.sigeh.app.core.model.JornadaSemanal
import mx.sigeh.app.core.network.ApiErrorParser
import mx.sigeh.app.core.network.lecturas.LecturasApi
import retrofit2.HttpException

/**
 * Programación semanal real (`GET /programaciones`, todas las páginas). Los nombres salen de
 * `GET /empleados` solo si se piden; si el padrón falla (403, 503, red) la programación se muestra
 * igual con «Trabajador ··· abcd».
 */
class RemotoProgramacionRepository @Inject constructor(
    private val api: LecturasApi,
    private val parser: ApiErrorParser,
) : ProgramacionRepository {

    override suspend fun semana(
        hospitalId: String,
        servicioId: String,
        desde: String,
        hasta: String,
        conNombres: Boolean,
        fechaPadron: String,
    ): ResultadoProgramacion = try {
        val jornadas = paginar { cursor ->
            api.programaciones(hospitalId, servicioId, desde, hasta, cursor, LecturasApi.LIMITE_PAGINA)
                .let { it.items to it.nextCursor }
        }
        val nombres = if (conNombres) padron(hospitalId, servicioId, fechaPadron) else null
        ResultadoProgramacion.Exito(
            jornadas = jornadas.map {
                JornadaSemanal(
                    programacionId = it.programacionId,
                    empleadoId = it.empleadoId,
                    nombre = nombres?.get(it.empleadoId),
                    horarioId = it.horarioId,
                    turnoCodigo = it.turnoCodigo,
                    inicioProgramado = it.inicioProgramado,
                    finProgramado = it.finProgramado,
                    estado = it.estado,
                )
            },
            nombresDisponibles = nombres != null,
        )
    } catch (e: HttpException) {
        parser.parse(e).aResultadoProgramacion()
    } catch (_: IOException) {
        ResultadoProgramacion.Fallida
    } catch (_: SerializationException) {
        ResultadoProgramacion.Fallida
    }

    /** Padrón vigente: empleadoId → «nombres primerApellido». Null si no se pudo leer. */
    private suspend fun padron(hospitalId: String, servicioId: String, fecha: String): Map<String, String>? = try {
        paginar { cursor ->
            api.empleados(hospitalId, servicioId, fecha, cursor, LecturasApi.LIMITE_PAGINA).let { it.items to it.nextCursor }
        }.associate { it.empleadoId to "${it.nombres} ${it.primerApellido}".trim() }
    } catch (_: HttpException) {
        null
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    /** Recorre las páginas con el mismo filtro hasta que no haya cursor (o se repita, o se llegue al tope). */
    private suspend fun <T> paginar(pagina: suspend (cursor: String?) -> Pair<List<T>, String?>): List<T> {
        val todos = mutableListOf<T>()
        val vistos = mutableSetOf<String>()
        var cursor: String? = null
        repeat(LecturasApi.MAXIMO_PAGINAS) {
            val (items, siguiente) = pagina(cursor)
            todos += items
            if (siguiente.isNullOrBlank() || !vistos.add(siguiente)) return todos
            cursor = siguiente
        }
        return todos
    }
}
