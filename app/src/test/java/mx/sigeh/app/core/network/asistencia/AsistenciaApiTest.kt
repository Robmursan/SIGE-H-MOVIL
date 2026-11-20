package mx.sigeh.app.core.network.asistencia

import java.util.UUID
import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.aResultadoCaptura
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.network.ApiErrorParser
import mx.sigeh.app.core.network.AuthInterceptor
import mx.sigeh.app.core.network.IdempotencyKeyInterceptor
import mx.sigeh.app.core.network.RequestIdInterceptor
import mx.sigeh.app.core.network.SesionAuthenticator
import mx.sigeh.app.core.network.TokenRefresher
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.testutil.ContratoAsistencia
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import mx.sigeh.app.testutil.respuestaJson
import mx.sigeh.app.testutil.retrofitPara
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * `GET /pases-lista` y `PUT /programaciones/{id}/asistencia` contra un servidor simulado con el
 * contrato `sigeh-v1`, usando la cadena real de OkHttp de la app (sin backend real).
 */
class AsistenciaApiTest {

    private val servidor = MockWebServer()
    private val reloj = RelojFijo(5_000_000L)
    private val parser = ApiErrorParser(SigehJson)
    private lateinit var sesiones: SessionStoreEnMemoria
    private lateinit var iamApi: IamApi
    private lateinit var api: AsistenciaApi

    @Before
    fun iniciar() {
        servidor.start()
        sesiones = SessionStoreEnMemoria(
            Session(
                usuarioId = ContratoIam.USUARIO_ID,
                nombreCorto = "cuenta_ficticia",
                accessToken = "acceso-1",
                refreshToken = ContratoIam.REFRESH_1,
                sessionId = ContratoIam.SESION_ID,
                accessExpiraEnMillis = reloj.ahora + 200_000,
            ),
        )
        val refresher = TokenRefresher(sesiones, Provider { iamApi }, reloj)
        // Mismo orden que NetworkModule.provideOkHttpClient.
        val cliente = OkHttpClient.Builder()
            .addInterceptor(RequestIdInterceptor())
            .addInterceptor(IdempotencyKeyInterceptor())
            .addInterceptor(AuthInterceptor(refresher))
            .authenticator(SesionAuthenticator(refresher))
            .build()
        val retrofit = retrofitPara(servidor, cliente)
        iamApi = retrofit.create(IamApi::class.java)
        api = retrofit.create(AsistenciaApi::class.java)
    }

    @After
    fun detener() = servidor.close()

    // --- GET /pases-lista -------------------------------------------------------------------

    @Test
    fun pasesLista_sinOpcionales_enviaSoloLosObligatoriosYElLimite() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA))

        val pagina = api.pasesLista(
            hospitalId = ContratoIam.HOSPITAL_ID,
            servicioId = ContratoIam.SERVICIO_ID,
            fecha = ContratoAsistencia.FECHA,
            horarioId = null,
            cursor = null,
            limit = AsistenciaApi.LIMITE_PAGINA,
        )

        assertEquals(3, pagina.items.size)
        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/v1/pases-lista", peticion.url.encodedPath)
        assertEquals("Bearer acceso-1", peticion.headers["Authorization"])
        assertParametros(
            peticion,
            "hospitalId" to ContratoIam.HOSPITAL_ID,
            "servicioId" to ContratoIam.SERVICIO_ID,
            "fecha" to ContratoAsistencia.FECHA,
            "limit" to "100",
        )
    }

    @Test
    fun pasesLista_conHorarioYCursor_losEnviaUnaSolaVez() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.PAGINA_VACIA))

        val pagina = api.pasesLista(
            ContratoIam.HOSPITAL_ID,
            ContratoIam.SERVICIO_ID,
            ContratoAsistencia.FECHA,
            horarioId = ContratoAsistencia.HORARIO_TM,
            cursor = ContratoAsistencia.CURSOR,
            limit = AsistenciaApi.LIMITE_PAGINA,
        )

        assertTrue(pagina.items.isEmpty())
        assertParametros(
            servidor.takeRequest(),
            "hospitalId" to ContratoIam.HOSPITAL_ID,
            "servicioId" to ContratoIam.SERVICIO_ID,
            "fecha" to ContratoAsistencia.FECHA,
            "horarioId" to ContratoAsistencia.HORARIO_TM,
            "cursor" to ContratoAsistencia.CURSOR,
            "limit" to "100",
        )
    }

    // --- PUT /programaciones/{id}/asistencia ------------------------------------------------

    @Test
    fun capturar_enviaRutaClaveBearerYCuerpoExactos() = runTest {
        val clave = UUID.randomUUID().toString()
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse("PARCIAL")))

        val recibo = api.capturar(
            ContratoAsistencia.PROGRAMACION_PENDIENTE,
            clave,
            CapturarAsistenciaDto.primeraCaptura(EstadoCaptura.PARCIAL),
        )

        assertEquals(EstadoCaptura.PARCIAL, recibo.estadoOperativo)
        assertEquals(ContratoAsistencia.EVENTO_GRANDE, recibo.eventoId)
        val peticion = servidor.takeRequest()
        assertEquals("PUT", peticion.method)
        assertEquals("/api/v1/programaciones/${ContratoAsistencia.PROGRAMACION_PENDIENTE}/asistencia", peticion.url.encodedPath)
        assertEquals("Bearer acceso-1", peticion.headers["Authorization"])
        assertEquals(clave, peticion.headers["Idempotency-Key"])
        assertTrue("Idempotency-Key debe ser UUID v4", UUID_V4.matches(peticion.headers["Idempotency-Key"]!!))
        assertEquals(1, peticion.headers.values("Idempotency-Key").size)
        assertTrue(peticion.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals("""{"estado":"PARCIAL","expectedRevision":"0"}""", peticion.body!!.utf8())
    }

    @Test
    fun capturar_401_renuevaYReintentaConLaMismaClaveYElMismoCuerpo() = runTest {
        val clave = UUID.randomUUID().toString()
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-2", ContratoIam.REFRESH_2)))
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()))

        val recibo = api.capturar(
            ContratoAsistencia.PROGRAMACION_PENDIENTE,
            clave,
            CapturarAsistenciaDto.primeraCaptura(EstadoCaptura.PRESENTE),
        )

        assertEquals(EstadoCaptura.PRESENTE, recibo.estadoOperativo)
        val original = servidor.takeRequest()
        assertEquals("/api/v1/auth/refresh", servidor.takeRequest().url.encodedPath)
        val reintento = servidor.takeRequest()
        assertEquals(3, servidor.requestCount)
        assertEquals("Bearer acceso-2", reintento.headers["Authorization"])
        assertEquals("Misma clave en el reintento", clave, original.headers["Idempotency-Key"])
        assertEquals("Misma clave en el reintento", clave, reintento.headers["Idempotency-Key"])
        assertEquals(original.body!!.utf8(), reintento.body!!.utf8())
    }

    // --- Errores reales del backend → ResultadoCaptura (prompt M2 §6.6) ----------------------

    @Test
    fun capturar_erroresDelBackend_seTraducenSegunLaTabla() = runTest {
        val casos = listOf(
            Triple(400, ContratoIam.errorNest(400, "Identificador o clave inválidos", "Bad Request"), ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA)),
            Triple(400, ContratoIam.errorNest(400, "Entrada inválida", "Bad Request"), ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA)),
            Triple(403, ContratoIam.errorNest(403, "Acceso denegado", "Forbidden"), ResultadoCaptura.SinPermiso),
            Triple(404, ContratoIam.errorNest(404, "Programación no disponible", "Not Found"), ResultadoCaptura.NoEncontrada),
            Triple(404, ContratoIam.errorNest(404, "Contexto no disponible", "Not Found"), ResultadoCaptura.NoEncontrada),
            Triple(409, ContratoIam.errorNest(409, "YA_REGISTRADA", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA)),
            Triple(409, ContratoIam.errorNest(409, "FUERA_VENTANA", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.FUERA_VENTANA)),
            Triple(409, ContratoIam.errorNest(409, "PROGRAMACION_NO_CAPTURABLE", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.PROGRAMACION_NO_CAPTURABLE)),
            Triple(409, ContratoIam.errorNest(409, "CONTRATO_NO_VIGENTE", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.CONTRATO_NO_VIGENTE)),
            Triple(409, ContratoIam.errorNest(409, "CIERRE_EXISTENTE", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.CIERRE_EXISTENTE)),
            Triple(409, ContratoIam.errorNest(409, "IDEMPOTENCY_CONFLICT", "Conflict"), ResultadoCaptura.Rechazada(MotivoRechazo.CONFLICTO_IDEMPOTENCIA)),
            Triple(503, ContratoIam.errorNest(503, "Asistencia no habilitada", "Service Unavailable"), ResultadoCaptura.NoHabilitada),
            Triple(503, ContratoIam.errorNest(503, "Asistencia no disponible; reintente con la misma clave", "Service Unavailable"), ResultadoCaptura.Incierta),
            Triple(500, ContratoIam.errorNest(500, "Internal server error", "Internal Server Error"), ResultadoCaptura.Incierta),
        )

        casos.forEach { (codigo, cuerpo, esperado) ->
            servidor.enqueue(respuestaJson(codigo, cuerpo))
            val error = capturarEsperandoError()
            assertEquals("HTTP $codigo $cuerpo", esperado, parser.parse(error).aResultadoCaptura())
        }
    }

    @Test
    fun capturar_401SinRenovacionPosible_esSesionTerminada() = runTest {
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Sesión inválida", "Unauthorized")))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))

        val error = capturarEsperandoError()

        assertEquals(ResultadoCaptura.SesionTerminada, parser.parse(error).aResultadoCaptura())
        assertTrue("El refresh rechazado marca la sesión vencida", sesiones.actual()!!.vencida)
        assertEquals("Solo la petición original y un refresh", 2, servidor.requestCount)
    }

    @Test
    fun error429_leeRetryAfterDelEncabezado() = runTest {
        servidor.enqueue(respuestaJson(429, """{"statusCode":429,"message":"Demasiados intentos"}""", "Retry-After" to "42"))

        val error = parser.parse(capturarEsperandoError())

        assertEquals(42L, error.reintentarEnSegundos)
        assertEquals("Demasiados intentos", error.message)
        assertEquals(ResultadoCaptura.Incierta, error.aResultadoCaptura())
    }

    private suspend fun capturarEsperandoError(): HttpException {
        try {
            api.capturar(
                ContratoAsistencia.PROGRAMACION_PENDIENTE,
                UUID.randomUUID().toString(),
                CapturarAsistenciaDto.primeraCaptura(EstadoCaptura.PRESENTE),
            )
        } catch (e: HttpException) {
            return e
        }
        fail("Se esperaba un error HTTP")
        throw AssertionError()
    }

    /** El query lleva exactamente estos parámetros, cada uno una vez y sin valores vacíos. */
    private fun assertParametros(peticion: RecordedRequest, vararg esperados: Pair<String, String>) {
        val url = peticion.url
        assertEquals(esperados.map { it.first }.toSet(), url.queryParameterNames)
        esperados.forEach { (nombre, valor) ->
            assertEquals("$nombre una sola vez", listOf(valor), url.queryParameterValues(nombre))
        }
    }

    private companion object {
        val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
