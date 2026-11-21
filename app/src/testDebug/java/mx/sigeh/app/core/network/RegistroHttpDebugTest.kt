package mx.sigeh.app.core.network

import mockwebserver3.MockWebServer
import mx.sigeh.app.testutil.ContratoAsistencia
import mx.sigeh.app.testutil.respuestaJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Privacidad del registro HTTP de debug (prompt M2, regla 13): no deben llegar a Logcat tokens,
 * cuerpos de `/auth/...` ni la `Idempotency-Key`. Solo existe en debug (source set `testDebug`).
 */
class RegistroHttpDebugTest {

    private val servidor = MockWebServer()
    private val lineas = mutableListOf<String>()
    private lateinit var cliente: OkHttpClient
    private val json = "application/json".toMediaType()

    @Before
    fun iniciar() {
        servidor.start()
        cliente = OkHttpClient.Builder()
            .addInterceptor(DebugNetworkModule.interceptorDeRegistro { lineas += it })
            .build()
    }

    @After
    fun detener() = servidor.close()

    private val registro: String get() = lineas.joinToString("\n")

    @Test
    fun captura_ocultaBearerEIdempotencyKey() {
        val clave = "3f2c9e1a-0000-4000-8000-00000000c1a7"
        servidor.enqueue(respuestaJson(200, ContratoAsistencia.acuse()))

        cliente.newCall(
            Request.Builder()
                .url(servidor.url("/api/v1/programaciones/p-02/asistencia"))
                .header("Authorization", "Bearer token-secreto")
                .header(IdempotencyKeyInterceptor.ENCABEZADO, clave)
                .put("""{"estado":"PRESENTE","expectedRevision":"0"}""".toRequestBody(json))
                .build(),
        ).execute().close()

        assertTrue("Se registra la petición", registro.contains("PUT"))
        assertTrue(registro.contains("Idempotency-Key: ██"))
        assertFalse("La clave no aparece", registro.contains(clave))
        assertFalse("El token no aparece", registro.contains("token-secreto"))
    }

    @Test
    fun rutasDeAutenticacion_sinCuerpos() {
        servidor.enqueue(respuestaJson(200, """{"accessToken":"acceso-secreto","refreshToken":"refresh-secreto","tokenType":"Bearer","expiresIn":300,"sessionId":"s"}"""))

        cliente.newCall(
            Request.Builder()
                .url(servidor.url("/api/v1/auth/login"))
                .post("""{"identificador":"cuenta_ficticia","password":"contrasena-secreta"}""".toRequestBody(json))
                .build(),
        ).execute().close()

        assertTrue(registro.contains("/api/v1/auth/login"))
        assertFalse("Contraseña", registro.contains("contrasena-secreta"))
        assertFalse("Access token", registro.contains("acceso-secreto"))
        assertFalse("Refresh token", registro.contains("refresh-secreto"))
    }
}
