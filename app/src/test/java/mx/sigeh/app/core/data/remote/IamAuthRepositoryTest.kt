package mx.sigeh.app.core.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.auth.AsignacionSesion
import mx.sigeh.app.core.data.ResultadoLogin
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.respuestaJson
import mx.sigeh.app.testutil.retrofitPara
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cliente IAM contra un servidor simulado con el contrato de SIGE-H-WEB (sin backend real). */
class IamAuthRepositoryTest {

    private val servidor = MockWebServer()
    private val reloj = RelojFijo(1_000_000L)
    private lateinit var repositorio: IamAuthRepository

    @Before
    fun iniciar() {
        servidor.start()
        repositorio = IamAuthRepository(retrofitPara(servidor).create(IamApi::class.java), reloj)
    }

    @After
    fun detener() = servidor.close()

    @Test
    fun login_exitoso_guardaTokensExpiracionYAsignacionesReales() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoIam.tokens("acceso-1", ContratoIam.REFRESH_1)))
        servidor.enqueue(respuestaJson(200, ContratoIam.PERFIL))

        val resultado = repositorio.iniciarSesion("cuenta_ficticia", "contrasena-ficticia-123")

        val sesion = (resultado as ResultadoLogin.Exito).sesion
        assertEquals(ContratoIam.USUARIO_ID, sesion.usuarioId)
        assertEquals("cuenta_ficticia", sesion.nombreCorto)
        assertEquals("Se guarda cifrado para la pantalla de Cuenta", "cuenta_ficticia", sesion.identificador)
        assertEquals("acceso-1", sesion.accessToken)
        assertEquals(ContratoIam.REFRESH_1, sesion.refreshToken)
        assertEquals(ContratoIam.SESION_ID, sesion.sessionId)
        assertEquals(reloj.ahora + 300_000, sesion.accessExpiraEnMillis)
        assertEquals(
            listOf(
                AsignacionSesion(
                    RolAlcance.TITULAR,
                    ContratoIam.HOSPITAL_ID,
                    ContratoIam.DIVISION_ID,
                    ContratoIam.SERVICIO_ID,
                    listOf("PERMISO_FICTICIO"),
                ),
                AsignacionSesion(rol = null, hospitalId = ContratoIam.HOSPITAL_ID),
            ),
            sesion.asignaciones,
        )

        val login = servidor.takeRequest()
        assertEquals("POST", login.method)
        assertEquals("/api/v1/auth/login", login.url.encodedPath)
        assertNull("El login no lleva bearer", login.headers["Authorization"])
        val cuerpo = SigehJson.parseToJsonElement(login.body!!.utf8()).jsonObject
        assertEquals("cuenta_ficticia", cuerpo["identificador"]!!.jsonPrimitive.content)
        assertEquals("contrasena-ficticia-123", cuerpo["password"]!!.jsonPrimitive.content)

        val perfil = servidor.takeRequest()
        assertEquals("GET", perfil.method)
        assertEquals("/api/v1/auth/me", perfil.url.encodedPath)
        assertEquals("Bearer acceso-1", perfil.headers["Authorization"])
    }

    @Test
    fun respuesta401_esCredencialGenerica() = runTest {
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))

        assertEquals(ResultadoLogin.CredencialesInvalidas, repositorio.iniciarSesion("cuenta_ficticia", "incorrecta"))
        assertEquals("No se pide el perfil tras un login fallido", 1, servidor.requestCount)
    }

    @Test
    fun respuesta429_esLimiteDeIntentosConEspera() = runTest {
        // El 429 real de IAM es {statusCode, message}, sin `error` ni Retry-After: se esperan 60 s.
        servidor.enqueue(respuestaJson(429, """{"statusCode":429,"message":"Demasiados intentos"}"""))
        assertEquals(ResultadoLogin.LimiteIntentos(esperaSegundos = 60), repositorio.iniciarSesion("cuenta_ficticia", "x"))

        servidor.enqueue(respuestaJson(429, """{"statusCode":429,"message":"Demasiados intentos"}""", "Retry-After" to "15"))
        assertEquals(ResultadoLogin.LimiteIntentos(esperaSegundos = 15), repositorio.iniciarSesion("cuenta_ficticia", "x"))
    }

    @Test
    fun respuesta503_iamDeshabilitado_esServicioNoDisponible() = runTest {
        // Respuesta real mientras el backend tenga IAM_V2_ENABLED=false.
        servidor.enqueue(respuestaJson(503, ContratoIam.errorNest(503, "IAM no disponible", "Service Unavailable")))

        assertEquals(ResultadoLogin.ServicioNoDisponible, repositorio.iniciarSesion("cuenta_ficticia", "x"))
    }

    @Test
    fun sinServidor_esSinConexion() = runTest {
        servidor.close()

        assertEquals(ResultadoLogin.SinConexion, repositorio.iniciarSesion("cuenta_ficticia", "x"))
    }

    @Test
    fun logout_llamaAlServidorYToleraErrores() = runTest {
        servidor.enqueue(respuestaJson(204, ""))
        repositorio.cerrarSesion()
        val logout = servidor.takeRequest()
        assertEquals("POST", logout.method)
        assertEquals("/api/v1/auth/logout", logout.url.encodedPath)

        servidor.close()
        repositorio.cerrarSesion() // sin red: no debe lanzar, la purga local sigue
        assertTrue(true)
    }
}
