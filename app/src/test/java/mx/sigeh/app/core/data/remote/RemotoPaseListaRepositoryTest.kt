package mx.sigeh.app.core.data.remote

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.network.ApiErrorParser
import mx.sigeh.app.testutil.ClienteDePrueba
import mx.sigeh.app.testutil.ContratoAsistencia
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.respuestaJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repositorio real del pase de lista contra un servidor simulado con el contrato `sigeh-v1`. */
class RemotoPaseListaRepositoryTest {

    private val servidor = MockWebServer()
    private val reloj = RelojFijo(5_000_000L)
    private lateinit var cliente: ClienteDePrueba
    private lateinit var repositorio: RemotoPaseListaRepository

    @Before
    fun iniciar() {
        servidor.start()
        prepararRepositorio()
    }

    @After
    fun detener() = servidor.close()

    private fun prepararRepositorio(timeoutLecturaMs: Long? = null) {
        cliente = ClienteDePrueba(servidor, reloj, timeoutLecturaMs)
        repositorio = RemotoPaseListaRepository(cliente.crear(), ApiErrorParser(SigehJson))
    }

    private suspend fun consultar(cursor: String? = null, horarioId: String? = null) = repositorio.consultar(
        hospitalId = ContratoIam.HOSPITAL_ID,
        servicioId = ContratoIam.SERVICIO_ID,
        fecha = ContratoAsistencia.FECHA,
        horarioId = horarioId,
        cursor = cursor,
    )

    private suspend fun capturar(clave: String = UUID.randomUUID().toString(), estado: EstadoCaptura = EstadoCaptura.PRESENTE) =
        repositorio.capturar(ContratoAsistencia.PROGRAMACION_PENDIENTE, estado, clave)

    // --- Consulta ------------------------------------------------------------------------------

    @Test
    fun consultar_opcionalesEnBlancoNoSeEnvian_yLaPaginaQuedaEnCache() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA))

        val resultado = consultar(cursor = " ", horarioId = "")

        val pagina = (resultado as ResultadoConsulta.Exito).pagina
        assertEquals(3, pagina.jornadas.size)
        assertEquals(ContratoAsistencia.ZONA, pagina.zonaHoraria)
        assertEquals(ContratoAsistencia.CURSOR, pagina.siguienteCursor)
        val url = servidor.takeRequest().url
        assertEquals(setOf("hospitalId", "servicioId", "fecha", "limit"), url.queryParameterNames)
        assertEquals("100", url.queryParameter("limit"))

        val capturada = repositorio.jornada(ContratoAsistencia.PROGRAMACION_CAPTURADA)!!
        assertEquals("Ana Sofía Ficticia", capturada.nombre)
        assertEquals(ContratoAsistencia.EVENTO_GRANDE, capturada.ultimaCaptura!!.eventoId)
    }

    @Test
    fun consultar_cargarMasAgregaALaCache_yUnaPrimeraPaginaLaReemplaza() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA))
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA_VACIA))
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA_VACIA))

        consultar()
        consultar(cursor = ContratoAsistencia.CURSOR)
        servidor.takeRequest()
        assertEquals(ContratoAsistencia.CURSOR, servidor.takeRequest().url.queryParameter("cursor"))
        assertNotNull("«Cargar más» conserva lo anterior", repositorio.jornada(ContratoAsistencia.PROGRAMACION_PENDIENTE))

        consultar()
        assertNull("Otra consulta desde el inicio reemplaza la caché", repositorio.jornada(ContratoAsistencia.PROGRAMACION_PENDIENTE))
    }

    @Test
    fun consultar_erroresDelBackend() = runTest {
        val casos = listOf(
            Triple(403, ContratoIam.errorNest(403, "Acceso denegado", "Forbidden"), ResultadoConsulta.SinPermiso),
            Triple(404, ContratoIam.errorNest(404, "Contexto no disponible", "Not Found"), ResultadoConsulta.ContextoNoDisponible),
            Triple(503, ContratoIam.errorNest(503, "Asistencia no habilitada", "Service Unavailable"), ResultadoConsulta.NoHabilitada),
            Triple(503, ContratoIam.errorNest(503, "Asistencia no disponible; reintente con la misma clave", "Service Unavailable"), ResultadoConsulta.Fallida),
            Triple(400, ContratoIam.errorNest(400, "Fecha inválida", "Bad Request"), ResultadoConsulta.Fallida),
        )
        casos.forEach { (codigo, cuerpo, esperado) ->
            servidor.enqueue(respuestaJson(codigo, cuerpo))
            assertEquals("HTTP $codigo $cuerpo", esperado, consultar())
        }
    }

    @Test
    fun consultar_401SinRenovacion_esSesionTerminada() = runTest {
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Sesión inválida", "Unauthorized")))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))

        assertEquals(ResultadoConsulta.SesionTerminada, consultar())
    }

    @Test
    fun consultar_sinServidor_esFallida() = runTest {
        servidor.close()

        assertEquals(ResultadoConsulta.Fallida, consultar())
    }

    // --- Captura -------------------------------------------------------------------------------

    @Test
    fun capturar_confirmada_enviaElContratoYGuardaElAcuse() = runTest {
        val clave = UUID.randomUUID().toString()
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse("AUSENTE")))

        val resultado = capturar(clave, EstadoCaptura.AUSENTE)

        val acuse = (resultado as ResultadoCaptura.Confirmada).acuse
        assertEquals(EstadoCaptura.AUSENTE, acuse.estado)
        assertEquals(ContratoAsistencia.EVENTO_GRANDE, acuse.eventoId)
        assertEquals(acuse, repositorio.acuse(ContratoAsistencia.PROGRAMACION_PENDIENTE))
        val peticion = servidor.takeRequest()
        assertEquals("PUT", peticion.method)
        assertEquals("Bearer acceso-1", peticion.headers["Authorization"])
        assertEquals(clave, peticion.headers["Idempotency-Key"])
        assertEquals("""{"estado":"AUSENTE","expectedRevision":"0"}""", peticion.body!!.utf8())
    }

    @Test
    fun capturar_erroresDelBackend_seTraducenSegunLaTabla() = runTest {
        val casos = listOf(
            Triple(400, ContratoIam.errorNest(400, "Entrada inválida", "Bad Request"), ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA)),
            Triple(403, ContratoIam.errorNest(403, "Acceso denegado", "Forbidden"), ResultadoCaptura.SinPermiso),
            Triple(404, ContratoIam.errorNest(404, "Programación no disponible", "Not Found"), ResultadoCaptura.NoEncontrada),
            Triple(409, ContratoIam.errorNest(409, "YA_REGISTRADA", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA)),
            Triple(409, ContratoIam.errorNest(409, "FUERA_VENTANA", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.FUERA_VENTANA)),
            Triple(409, ContratoIam.errorNest(409, "PROGRAMACION_NO_CAPTURABLE", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.PROGRAMACION_NO_CAPTURABLE)),
            Triple(409, ContratoIam.errorNest(409, "CONTRATO_NO_VIGENTE", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.CONTRATO_NO_VIGENTE)),
            Triple(409, ContratoIam.errorNest(409, "CIERRE_EXISTENTE", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.CIERRE_EXISTENTE)),
            Triple(409, ContratoIam.errorNest(409, "IDEMPOTENCY_CONFLICT", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.CONFLICTO_IDEMPOTENCIA)),
            Triple(429, """{"statusCode":429,"message":"Demasiados intentos"}""", ResultadoCaptura.Incierta),
            Triple(503, ContratoIam.errorNest(503, "Asistencia no habilitada", "Service Unavailable"), ResultadoCaptura.NoHabilitada),
            Triple(503, ContratoIam.errorNest(503, "Asistencia no disponible; reintente con la misma clave", "Service Unavailable"), ResultadoCaptura.Incierta),
        )
        casos.forEach { (codigo, cuerpo, esperado) ->
            servidor.enqueue(respuestaJson(codigo, cuerpo))
            assertEquals("HTTP $codigo $cuerpo", esperado, capturar())
        }
        assertNull("Sin acuse no hay nada que mostrar", repositorio.acuse(ContratoAsistencia.PROGRAMACION_PENDIENTE))
    }

    @Test
    fun capturar_timeoutYLuegoExito_reutilizaLaMismaClave() = runTest {
        prepararRepositorio(timeoutLecturaMs = 300)
        val clave = UUID.randomUUID().toString()
        // El servidor tarda más que el timeout: el teléfono no sabe si quedó guardado.
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()).newBuilder().headersDelay(2, TimeUnit.SECONDS).build())
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()))

        assertEquals(ResultadoCaptura.Incierta, capturar(clave))
        val confirmado = capturar(clave)

        assertTrue(confirmado is ResultadoCaptura.Confirmada)
        val primero = servidor.takeRequest()
        val reintento = servidor.takeRequest()
        assertEquals("Sin reintentos ocultos de OkHttp", 2, servidor.requestCount)
        assertEquals(clave, primero.headers["Idempotency-Key"])
        assertEquals(clave, reintento.headers["Idempotency-Key"])
        assertEquals(primero.body!!.utf8(), reintento.body!!.utf8())
    }

    @Test
    fun capturar_401ConRefreshExitoso_reintentaConLaMismaClave() = runTest {
        val clave = UUID.randomUUID().toString()
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-2", ContratoIam.REFRESH_2)))
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()))

        assertTrue(capturar(clave) is ResultadoCaptura.Confirmada)

        val original = servidor.takeRequest()
        servidor.takeRequest() // refresh
        val reintento = servidor.takeRequest()
        assertEquals(clave, original.headers["Idempotency-Key"])
        assertEquals(clave, reintento.headers["Idempotency-Key"])
        assertEquals("Bearer acceso-2", reintento.headers["Authorization"])
    }

    @Test
    fun capturar_401SinRenovacion_esSesionTerminada() = runTest {
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Sesión inválida", "Unauthorized")))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))

        assertEquals(ResultadoCaptura.SesionTerminada, capturar())
        assertTrue(cliente.sesiones.actual()!!.vencida)
    }

    @Test
    fun limpiar_purgaJornadasYAcuses() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA))
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()))
        consultar()
        capturar()

        repositorio.limpiar()

        assertNull(repositorio.jornada(ContratoAsistencia.PROGRAMACION_CAPTURADA))
        assertNull(repositorio.acuse(ContratoAsistencia.PROGRAMACION_PENDIENTE))
    }
}
