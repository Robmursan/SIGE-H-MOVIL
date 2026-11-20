package mx.sigeh.app.core.network.iam

import javax.inject.Provider
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.network.AuthInterceptor
import mx.sigeh.app.core.network.SesionAuthenticator
import mx.sigeh.app.core.network.TokenRefresher
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
import org.junit.Before
import org.junit.Test

/** `GET /auth/sessions` y `DELETE /auth/sessions/{sessionId}` (operationId `iamSessions` / `iamRevokeSession`). */
class IamSesionesApiTest {

    private val servidor = MockWebServer()
    private val reloj = RelojFijo(1_000_000L)
    private lateinit var api: IamApi

    @Before
    fun iniciar() {
        servidor.start()
        val sesiones = SessionStoreEnMemoria(
            Session(
                usuarioId = ContratoIam.USUARIO_ID,
                nombreCorto = "cuenta_ficticia",
                accessToken = "acceso-1",
                refreshToken = ContratoIam.REFRESH_1,
                sessionId = ContratoIam.SESION_ID,
                accessExpiraEnMillis = reloj.ahora + 200_000,
            ),
        )
        val refresher = TokenRefresher(sesiones, Provider { api }, reloj)
        val cliente = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(refresher))
            .authenticator(SesionAuthenticator(refresher))
            .build()
        api = retrofitPara(servidor, cliente).create(IamApi::class.java)
    }

    @After
    fun detener() = servidor.close()

    @Test
    fun sesiones_llevaBearerYLeeLaLista() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoIam.SESIONES))

        val sesiones = api.sesiones()

        assertEquals(3, sesiones.size)
        assertTrue(sesiones.single { it.current }.sessionId == ContratoIam.SESION_ID)
        val peticion = servidor.takeRequest()
        assertEquals("GET", peticion.method)
        assertEquals("/api/v1/auth/sessions", peticion.url.encodedPath)
        assertEquals("Bearer acceso-1", peticion.headers["Authorization"])
    }

    @Test
    fun revocarSesion_204_esExitoSinCuerpo() = runTest {
        val otra = "6a1f4e2c-0000-4000-8000-0000000000ac"
        servidor.enqueue(respuestaJson(204, ""))

        val respuesta = api.revocarSesion(otra)

        assertTrue(respuesta.isSuccessful)
        assertEquals(204, respuesta.code())
        val peticion = servidor.takeRequest()
        assertEquals("DELETE", peticion.method)
        assertEquals("/api/v1/auth/sessions/$otra", peticion.url.encodedPath)
        assertEquals("Bearer acceso-1", peticion.headers["Authorization"])
        assertNull("El DELETE no lleva cuerpo", peticion.body?.utf8()?.takeIf { it.isNotEmpty() })
    }

    @Test
    fun revocarSesionAjena_404_noLanza() = runTest {
        servidor.enqueue(respuestaJson(404, ContratoIam.errorNest(404, "Sesión no disponible", "Not Found")))

        val respuesta = api.revocarSesion("6a1f4e2c-0000-4000-8000-0000000000ff")

        assertFalse(respuesta.isSuccessful)
        assertEquals(404, respuesta.code())
    }
}
