package mx.sigeh.app.core.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.data.ResultadoRevocacion
import mx.sigeh.app.core.data.ResultadoSesiones
import mx.sigeh.app.testutil.ClienteDePrueba
import mx.sigeh.app.testutil.ContratoIam
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.respuestaJson
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemotoSesionesRepositoryTest {

    private val servidor = MockWebServer()
    private lateinit var repositorio: RemotoSesionesRepository

    @Before
    fun iniciar() {
        servidor.start()
        val cliente = ClienteDePrueba(servidor, RelojFijo(1_000_000L))
        repositorio = RemotoSesionesRepository(cliente.crear(), cliente.sesiones)
    }

    @After
    fun detener() = servidor.close()

    @Test
    fun sesiones_seConviertenAlDominio() = runTest {
        servidor.enqueue(respuestaJson(200, ContratoIam.SESIONES))

        val sesiones = (repositorio.sesiones() as ResultadoSesiones.Exito).sesiones

        assertEquals(3, sesiones.size)
        val actual = sesiones.single { it.actual }
        assertEquals(ContratoIam.SESION_ID, actual.sessionId)
        assertEquals("2026-11-20T13:00:00.000Z", actual.iniciadaEn)
        assertEquals("2026-11-20T21:00:00.000Z", actual.expiraEn)
        assertEquals("2026-11-20T13:30:00.000Z", actual.inactividadExpiraEn)
        assertNull(actual.revocadaEn)
        assertEquals(listOf(false, true, false), sesiones.map { it.revocada })
    }

    @Test
    fun sesiones_errores() = runTest {
        servidor.enqueue(respuestaJson(503, ContratoIam.errorNest(503, "IAM no disponible", "Service Unavailable")))
        assertEquals(ResultadoSesiones.Fallida, repositorio.sesiones())

        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        servidor.enqueue(respuestaJson(401, ContratoIam.errorNest(401, "Credenciales o sesión inválidas", "Unauthorized")))
        assertEquals(ResultadoSesiones.SesionTerminada, repositorio.sesiones())

        servidor.close()
        assertEquals(ResultadoSesiones.Fallida, repositorio.sesiones())
    }

    @Test
    fun revocar_laSesionDeEsteDispositivo_seIndicaParaPurgar() = runTest {
        servidor.enqueue(respuestaJson(204, ""))
        servidor.enqueue(respuestaJson(204, ""))

        assertEquals(ResultadoRevocacion.Revocada(eraLaActual = true), repositorio.revocar(ContratoIam.SESION_ID))
        assertEquals(
            ResultadoRevocacion.Revocada(eraLaActual = false),
            repositorio.revocar("6a1f4e2c-0000-4000-8000-0000000000ac"),
        )
        assertTrue(servidor.takeRequest().url.encodedPath.endsWith("/auth/sessions/${ContratoIam.SESION_ID}"))
    }

    @Test
    fun revocar_errores() = runTest {
        servidor.enqueue(respuestaJson(404, ContratoIam.errorNest(404, "Sesión no disponible", "Not Found")))
        assertEquals(ResultadoRevocacion.NoEncontrada, repositorio.revocar("6a1f4e2c-0000-4000-8000-0000000000ff"))

        servidor.enqueue(respuestaJson(503, ContratoIam.errorNest(503, "IAM no disponible", "Service Unavailable")))
        assertEquals(ResultadoRevocacion.Fallida, repositorio.revocar("6a1f4e2c-0000-4000-8000-0000000000ff"))

        servidor.close()
        assertEquals(ResultadoRevocacion.Fallida, repositorio.revocar("6a1f4e2c-0000-4000-8000-0000000000ff"))
    }
}
