package mx.sigeh.app.core.network

import mx.sigeh.app.core.model.SigehJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorParserTest {

    private val parser = ApiErrorParser(SigehJson)

    @Test
    fun formatoNestJs_realDelBackend() {
        val error = parser.parse(401, """{"statusCode":401,"message":"Credenciales o sesión inválidas","error":"Unauthorized"}""")

        assertEquals(TipoErrorApi.NO_AUTENTICADO, error.tipo)
        assertEquals("Credenciales o sesión inválidas", error.message)
        assertEquals("Unauthorized", error.code)
        assertFalse(error.retryable)
    }

    @Test
    fun formatoNestJs_validacionConListaDeMensajes() {
        val error = parser.parse(400, """{"statusCode":400,"message":["identificador must be a string","password should not be empty"],"error":"Bad Request"}""")

        assertEquals(TipoErrorApi.SOLICITUD_INVALIDA, error.tipo)
        assertEquals("identificador must be a string; password should not be empty", error.message)
    }

    @Test
    fun iamDeshabilitado503_esReintentable() {
        val error = parser.parse(503, """{"statusCode":503,"message":"IAM no disponible","error":"Service Unavailable"}""")

        assertEquals(TipoErrorApi.NO_DISPONIBLE, error.tipo)
        assertTrue(error.retryable)
    }

    @Test
    fun formatoDeLaEspecificacion_seSigueAceptando() {
        val error = parser.parse(409, """{"code":"REGISTRO_VALIDADO","message":"Conflicto","traceId":"t-1","retryable":false}""")

        assertEquals(TipoErrorApi.CONFLICTO, error.tipo)
        assertEquals("REGISTRO_VALIDADO", error.code)
        assertEquals("t-1", error.traceId)
    }

    @Test
    fun cuerpoVacioOInvalido_noFalla() {
        assertNull(parser.parse(502, "").message)
        assertNull(parser.parse(500, "<html>error</html>").code)
    }
}
