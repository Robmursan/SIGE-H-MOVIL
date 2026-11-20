package mx.sigeh.app.core.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.data.ResultadoProgramacion
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.network.ApiErrorParser
import mx.sigeh.app.testutil.ClienteDePrueba
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.respuestaJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `GET /programaciones` y `GET /empleados` (contrato `sigeh-v1`) contra un servidor simulado. */
class RemotoProgramacionRepositoryTest {

    private val servidor = MockWebServer()
    private lateinit var repositorio: RemotoProgramacionRepository

    private val desde = "2026-11-16T00:00:00-06:00"
    private val hasta = "2026-11-23T00:00:00-06:00"
    private val empleado1 = "8c3d0000-0000-4000-8000-0000000000a1"
    private val empleado2 = "8c3d0000-0000-4000-8000-00000000abcd"
    private val cursor = "8c3d0000-0000-4000-8000-0000000000ff"

    private fun programacion(id: Int, empleadoId: String, estado: String = "PROGRAMADA") = """
        {"programacionId":"8c3d0000-0000-4000-8000-00000000000$id","empleadoId":"$empleadoId","servicioId":"${ContratoIam.SERVICIO_ID}",
         "horarioId":null,"turnoCodigo":"TM","inicioProgramado":"2026-11-16T07:00:00.000-06:00",
         "finProgramado":"2026-11-16T14:30:00.000-06:00","estado":"$estado"}
    """.trimIndent()

    private fun pagina(items: String, siguiente: String? = null) =
        """{"items":[$items],"nextCursor":${siguiente?.let { "\"$it\"" } ?: "null"},"limit":100,"order":"id:asc"}"""

    @Before
    fun iniciar() {
        servidor.start()
        repositorio = RemotoProgramacionRepository(ClienteDePrueba(servidor, RelojFijo(1_000_000L)).crear(), ApiErrorParser(SigehJson))
    }

    @After
    fun detener() = servidor.close()

    private suspend fun semana(conNombres: Boolean) = repositorio.semana(
        hospitalId = ContratoIam.HOSPITAL_ID,
        servicioId = ContratoIam.SERVICIO_ID,
        desde = desde,
        hasta = hasta,
        conNombres = conNombres,
        fechaPadron = "2026-11-16",
    )

    @Test
    fun semana_recorreLasPaginasYCruzaNombres() = runTest {
        servidor.enqueue(respuestaJson(200, pagina(programacion(1, empleado1), siguiente = cursor)))
        servidor.enqueue(respuestaJson(200, pagina(programacion(2, empleado2, "CANCELADA"))))
        servidor.enqueue(respuestaJson(200, pagina("""{"empleadoId":"$empleado1","nombres":"Ana","primerApellido":"Ficticia","activo":true}""")))

        val resultado = semana(conNombres = true) as ResultadoProgramacion.Exito

        assertTrue(resultado.nombresDisponibles)
        assertEquals(2, resultado.jornadas.size)
        assertEquals("Ana Ficticia", resultado.jornadas[0].nombreVisible)
        assertEquals("Sin nombre en el padrón", "Trabajador ··· abcd", resultado.jornadas[1].nombreVisible)
        assertEquals(EstadoProgramacion.CANCELADA, resultado.jornadas[1].estado)

        val primera = servidor.takeRequest().url
        assertEquals("/api/v1/programaciones", primera.encodedPath)
        assertEquals(setOf("hospitalId", "servicioId", "desde", "hasta", "limit"), primera.queryParameterNames)
        assertEquals(desde, primera.queryParameter("desde"))
        assertEquals(hasta, primera.queryParameter("hasta"))
        assertEquals("100", primera.queryParameter("limit"))
        assertEquals("Misma ventana con el cursor", cursor, servidor.takeRequest().url.queryParameter("cursor"))
        val padron = servidor.takeRequest().url
        assertEquals("/api/v1/empleados", padron.encodedPath)
        assertEquals("2026-11-16", padron.queryParameter("fecha"))
    }

    @Test
    fun sinPermisoDePersonal_noSePideElPadron() = runTest {
        servidor.enqueue(respuestaJson(200, pagina(programacion(1, empleado2))))

        val resultado = semana(conNombres = false) as ResultadoProgramacion.Exito

        assertFalse(resultado.nombresDisponibles)
        assertNull(resultado.jornadas.single().nombre)
        assertEquals(1, servidor.requestCount)
    }

    @Test
    fun padronRechazado_laProgramacionSeMuestraSinNombres() = runTest {
        servidor.enqueue(respuestaJson(200, pagina(programacion(1, empleado2))))
        servidor.enqueue(respuestaJson(403, ContratoIam.errorNest(403, "Acceso denegado", "Forbidden")))

        val resultado = semana(conNombres = true) as ResultadoProgramacion.Exito

        assertFalse(resultado.nombresDisponibles)
        assertEquals("Trabajador ··· abcd", resultado.jornadas.single().nombreVisible)
    }

    @Test
    fun errores() = runTest {
        servidor.enqueue(respuestaJson(403, ContratoIam.errorNest(403, "Acceso denegado", "Forbidden")))
        assertEquals(ResultadoProgramacion.SinPermiso, semana(conNombres = true))

        servidor.enqueue(respuestaJson(503, ContratoIam.errorNest(503, "Lecturas aún no habilitadas", "Service Unavailable")))
        assertEquals(ResultadoProgramacion.NoHabilitada, semana(conNombres = true))

        servidor.enqueue(respuestaJson(503, ContratoIam.errorNest(503, "Lectura no disponible", "Service Unavailable")))
        assertEquals(ResultadoProgramacion.Fallida, semana(conNombres = true))

        servidor.enqueue(respuestaJson(400, ContratoIam.errorNest(400, "Intervalo inválido: máximo 168 horas", "Bad Request")))
        assertEquals(ResultadoProgramacion.Fallida, semana(conNombres = true))
    }

    @Test
    fun cursorRepetido_noEntraEnBucle() = runTest {
        repeat(3) { servidor.enqueue(respuestaJson(200, pagina(programacion(1, empleado1), siguiente = cursor))) }

        val resultado = semana(conNombres = false) as ResultadoProgramacion.Exito

        assertEquals(2, servidor.requestCount)
        assertEquals(2, resultado.jornadas.size)
    }
}
