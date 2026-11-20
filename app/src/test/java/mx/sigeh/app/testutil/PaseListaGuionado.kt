package mx.sigeh.app.testutil

import kotlinx.coroutines.CompletableDeferred
import mx.sigeh.app.core.auth.FacultadesAsistencia
import mx.sigeh.app.core.auth.ServicioActivo
import mx.sigeh.app.core.data.CachePaseLista
import mx.sigeh.app.core.data.PaseListaRepository
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.PaginaPaseLista
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.model.ValidacionAdministrativa

/** Una llamada a `capturar` tal como la recibió el repositorio. */
data class CapturaRecibida(val programacionId: String, val estado: EstadoCaptura, val clave: String)

/**
 * [PaseListaRepository] con respuestas programadas, para probar el ViewModel sin latencias.
 * - [zonaRespuesta] es la `zonaHoraria` que «envía el servidor».
 * - [resultados] se consumen en orden; sin resultados programados, la captura se confirma.
 * - Con [retener] la captura queda en curso hasta completar el `CompletableDeferred`.
 */
class PaseListaGuionado(
    var jornadas: List<JornadaPase> = DatosFicticios.jornadasDelDia("p", "2026-11-20", DatosFicticios.usuario.id),
    var zonaRespuesta: String = DatosFicticios.ZONA_HOSPITAL,
) : PaseListaRepository {

    val consultas = mutableListOf<Pair<String, String?>>()
    val capturas = mutableListOf<CapturaRecibida>()
    val resultados = ArrayDeque<ResultadoCaptura>()
    var resultadoConsulta: ResultadoConsulta? = null
    var retener: CompletableDeferred<Unit>? = null
    private val cache = CachePaseLista()

    override suspend fun consultar(
        hospitalId: String,
        servicioId: String,
        fecha: String,
        horarioId: String?,
        cursor: String?,
    ): ResultadoConsulta {
        consultas += fecha to horarioId
        resultadoConsulta?.let { return it }
        val pagina = PaginaPaseLista(
            hospitalId = hospitalId,
            servicioId = servicioId,
            fecha = fecha,
            zonaHoraria = zonaRespuesta,
            jornadas = jornadas.filter { horarioId == null || it.horarioId == horarioId },
            siguienteCursor = null,
        )
        cache.registrarPagina(pagina, primeraPagina = cursor == null)
        return ResultadoConsulta.Exito(pagina)
    }

    override suspend fun capturar(programacionId: String, estado: EstadoCaptura, idempotencyKey: String): ResultadoCaptura {
        capturas += CapturaRecibida(programacionId, estado, idempotencyKey)
        retener?.await()
        return resultados.removeFirstOrNull() ?: ResultadoCaptura.Confirmada(acuse(programacionId, estado))
    }

    override fun jornada(programacionId: String): JornadaPase? = cache.jornada(programacionId)

    override fun acuse(programacionId: String): AcuseCaptura? = cache.acuse(programacionId)

    override fun zonaHoraria(): String? = cache.zonaHoraria()

    override fun limpiar() = cache.limpiar()

    companion object {
        fun acuse(programacionId: String, estado: EstadoCaptura) = AcuseCaptura(
            programacionId = programacionId,
            asistenciaId = "asi-$programacionId",
            eventoId = "9007199254741000",
            estado = estado,
            validacion = ValidacionAdministrativa.PENDIENTE,
            actorId = DatosFicticios.usuario.id,
            registradoEn = "2026-11-20T13:05:12.000Z",
            revision = "1",
        )

        /** Servicio de Medicina Interna con captura, como lo deja el selector. */
        val SERVICIO = ServicioActivo(
            hospitalId = DatosFicticios.hospitalA.id,
            divisionId = DatosFicticios.medicinaInterna.divisionId,
            servicioId = DatosFicticios.medicinaInterna.id,
            rol = RolAlcance.TITULAR,
            facultades = FacultadesAsistencia(capturar = true, validar = true, consultarProgramacion = true, consultarPersonal = true),
            hospitalNombre = "Hospital A",
            divisionNombre = "División de Medicina",
            servicioNombre = "Medicina Interna",
        )
    }
}
