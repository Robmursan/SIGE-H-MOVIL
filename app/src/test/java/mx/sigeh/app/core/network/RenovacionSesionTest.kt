package mx.sigeh.app.core.network

import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.network.asistencia.AsistenciaApi
import mx.sigeh.app.core.network.asistencia.CapturarAsistenciaDto
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.testutil.ContratoAsistencia
import mx.sigeh.app.core.network.iam.LoginRequest
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import mx.sigeh.app.testutil.respuestaJson
import mx.sigeh.app.testutil.retrofitPara
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * Renovación del access token (300 s) con el refresh de un solo uso de IAM V2, usando la cadena real
 * de OkHttp de la app (AuthInterceptor + SesionAuthenticator + TokenRefresher) contra un servidor simulado.
 */
class RenovacionSesionTest {

    private val servidor = MockWebServer()
    private val reloj = RelojFijo(5_000_000L)
    private lateinit var sesiones: SessionStoreEnMemoria
    private lateinit var api: IamApi
    private lateinit var asistenciaApi: AsistenciaApi

    private fun sesionCon(expiraEnMillis: Long) = Session(
        usuarioId = ContratoIam.USUARIO_ID,
        nombreCorto = "cuenta_ficticia",
        accessToken = "acceso-1",
        refreshToken = ContratoIam.REFRESH_1,
        sessionId = ContratoIam.SESION_ID,
        accessExpiraEnMillis = expiraEnMillis,
    )

    private fun prepararCliente(sesion: Session) {
        sesiones = SessionStoreEnMemoria(sesion)
        val refresher = TokenRefresher(sesiones, Provider { api }, reloj)
        val cliente = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(refresher))
            .authenticator(SesionAuthenticator(refresher))
            .build()
        val retrofit = retrofitPara(servidor, cliente)
        api = retrofit.create(IamApi::class.java)
        asistenciaApi = retrofit.create(AsistenciaApi::class.java)
    }

    @Test
    fun tokenPorVencerAntesDeCapturar_seRenuevaYLaCapturaSaleUnaVezConSuClave() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 5_000))
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-2", ContratoIam.REFRESH_2)))
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()))
        val clave = "3f2c9e1a-0000-4000-8000-00000000c1a7"

        asistenciaApi.capturar(ContratoAsistencia.PROGRAMACION_PENDIENTE, clave, CapturarAsistenciaDto.primeraCaptura(EstadoCaptura.PRESENTE))

        assertEquals("/api/v1/auth/refresh", servidor.takeRequest().url.encodedPath)
        val captura = servidor.takeRequest()
        assertEquals("Bearer acceso-2", captura.headers["Authorization"])
        assertEquals(clave, captura.headers["Idempotency-Key"])
        assertEquals("El PUT sale una sola vez", 2, servidor.requestCount)
    }

    @Test
    fun sesiones_esRutaProtegida() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 200_000))
        servidor.enqueue(respuestaJson(200, ContratoIam.SESIONES))

        api.sesiones()

        assertEquals("Bearer acceso-1", servidor.takeRequest().headers["Authorization"])
    }

    @Before
    fun iniciar() = servidor.start()

    @After
    fun detener() = servidor.close()

    @Test
    fun respuesta401_renuevaUnaSolaVezYReintentaConElTokenNuevo() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 200_000))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-2", ContratoIam.REFRESH_2)))
        servidor.enqueue(respuestaJson(200, ContratoIam.PERFIL))

        val perfil = api.perfil(null)

        assertEquals(ContratoIam.USUARIO_ID, perfil.usuarioId)
        val primero = servidor.takeRequest()
        assertEquals("Bearer acceso-1", primero.headers["Authorization"])
        val renovacion = servidor.takeRequest()
        assertEquals("/api/v1/auth/refresh", renovacion.url.encodedPath)
        assertNull("El refresh no lleva bearer", renovacion.headers["Authorization"])
        assertEquals(
            ContratoIam.REFRESH_1,
            SigehJson.parseToJsonElement(renovacion.body!!.utf8()).jsonObject["refreshToken"]!!.jsonPrimitive.content,
        )
        assertEquals("Bearer acceso-2", servidor.takeRequest().headers["Authorization"])
        assertEquals(3, servidor.requestCount)

        val guardada = sesiones.actual()!!
        assertEquals("acceso-2", guardada.accessToken)
        assertEquals("El refresh rota: se guarda el nuevo", ContratoIam.REFRESH_2, guardada.refreshToken)
        assertEquals(reloj.ahora + 300_000, guardada.accessExpiraEnMillis)
    }

    @Test
    fun refreshRechazado_marcaSesionVencidaSinReintentarEnBucle() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 200_000))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        // Refresh revocado (por ejemplo, replay detectado o sesión cerrada en otro dispositivo).
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))

        try {
            api.perfil(null)
            fail("Se esperaba 401")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }

        assertTrue(sesiones.actual()!!.vencida)
        assertEquals("Solo la petición original y un refresh", 2, servidor.requestCount)
    }

    @Test
    fun refreshNoDisponible503_noInvalidaLaSesion() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 200_000))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        servidor.enqueue(respuestaJson(503, ContratoIam.errorNest(503, "IAM no disponible", "Service Unavailable")))

        try {
            api.perfil(null)
            fail("Se esperaba 401")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }

        assertFalse("Un 503 es transitorio: la sesión se conserva", sesiones.actual()!!.vencida)
        assertEquals(ContratoIam.REFRESH_1, sesiones.actual()!!.refreshToken)
    }

    @Test
    fun tokenPorVencer_seRenuevaAntesDeEnviarLaPeticion() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 10_000)) // menos que el margen de 30 s
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-2", ContratoIam.REFRESH_2)))
        servidor.enqueue(respuestaJson(200, ContratoIam.PERFIL))

        api.perfil(null)

        assertEquals("/api/v1/auth/refresh", servidor.takeRequest().url.encodedPath)
        assertEquals("Bearer acceso-2", servidor.takeRequest().headers["Authorization"])
        assertEquals(2, servidor.requestCount)
    }

    @Test
    fun rutasPublicas_noLlevanBearer() = runTest {
        prepararCliente(sesionCon(expiraEnMillis = reloj.ahora + 200_000))
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-9", ContratoIam.REFRESH_2)))

        api.login(LoginRequest("cuenta_ficticia", "contrasena-ficticia-123"))

        assertNull(servidor.takeRequest().headers["Authorization"])
    }
}
