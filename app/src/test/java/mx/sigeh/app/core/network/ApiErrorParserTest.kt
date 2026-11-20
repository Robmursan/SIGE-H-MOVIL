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
        assertNull(parser.parse(500, "<html>error</html>").causa)
    }

    // --- M2: causas reales de asistencia (SIGE-H-WEB 2d1ec62) ---------------------------------

    @Test
    fun conflictosDeAsistencia_elCodigoVieneEnMessage() {
        val codigos = mapOf(
            "YA_REGISTRADA" to CausaErrorApi.YA_REGISTRADA,
            "FUERA_VENTANA" to CausaErrorApi.FUERA_VENTANA,
            "PROGRAMACION_NO_CAPTURABLE" to CausaErrorApi.PROGRAMACION_NO_CAPTURABLE,
            "CONTRATO_NO_VIGENTE" to CausaErrorApi.CONTRATO_NO_VIGENTE,
            "CIERRE_EXISTENTE" to CausaErrorApi.CIERRE_EXISTENTE,
            "IDEMPOTENCY_CONFLICT" to CausaErrorApi.IDEMPOTENCY_CONFLICT,
        )
        codigos.forEach { (mensaje, causa) ->
            val error = parser.parse(409, """{"statusCode":409,"message":"$mensaje","error":"Conflict"}""")

            assertEquals(TipoErrorApi.CONFLICTO, error.tipo)
            assertEquals(mensaje, causa, error.causa)
            assertEquals("Conflict", error.code)
            assertFalse(error.retryable)
        }
    }

    @Test
    fun asistenciaNoHabilitada_noEsReintentable() {
        val error = parser.parse(503, """{"statusCode":503,"message":"Asistencia no habilitada","error":"Service Unavailable"}""")

        assertEquals(CausaErrorApi.ASISTENCIA_NO_HABILITADA, error.causa)
        assertFalse("Sin reintento automático", error.retryable)
    }

    @Test
    fun asistenciaIncierta_seReintentaConLaMismaClave() {
        val error = parser.parse(
            503,
            """{"statusCode":503,"message":"Asistencia no disponible; reintente con la misma clave","error":"Service Unavailable"}""",
        )

        assertEquals(CausaErrorApi.ASISTENCIA_INCIERTA, error.causa)
        assertTrue(error.retryable)
    }

    @Test
    fun errores400_403y404_reconocenElTextoReal() {
        assertEquals(CausaErrorApi.FECHA_INVALIDA, parser.parse(400, """{"statusCode":400,"message":"Fecha inválida","error":"Bad Request"}""").causa)
        assertEquals(CausaErrorApi.LIMITE_INVALIDO, parser.parse(400, """{"statusCode":400,"message":"Límite inválido","error":"Bad Request"}""").causa)
        assertEquals(CausaErrorApi.ACCESO_DENEGADO, parser.parse(403, """{"statusCode":403,"message":"Acceso denegado","error":"Forbidden"}""").causa)
        assertEquals(CausaErrorApi.CONTEXTO_NO_DISPONIBLE, parser.parse(404, """{"statusCode":404,"message":"Contexto no disponible","error":"Not Found"}""").causa)
        assertEquals(CausaErrorApi.PROGRAMACION_NO_DISPONIBLE, parser.parse(404, """{"statusCode":404,"message":"Programación no disponible","error":"Not Found"}""").causa)
    }

    @Test
    fun mensajeDesconocidoOConOtroCodigo_noTieneCausa() {
        assertNull(parser.parse(409, """{"statusCode":409,"message":"OTRO_MOTIVO","error":"Conflict"}""").causa)
        // El texto se reconoce solo con su código HTTP.
        assertNull(parser.parse(409, """{"statusCode":409,"message":"Acceso denegado","error":"Conflict"}""").causa)
        assertNull(parser.parse(400, """{"statusCode":400,"message":["fecha must match"],"error":"Bad Request"}""").causa)
    }

    @Test
    fun limite429DeIam_sinErrorNiRetryAfter_esperaSesentaSegundos() {
        val error = parser.parse(429, """{"statusCode":429,"message":"Demasiados intentos"}""")

        assertEquals(TipoErrorApi.LIMITE, error.tipo)
        assertNull("El 429 de IAM no trae `error`", error.code)
        assertEquals("Demasiados intentos", error.message)
        assertEquals(ApiErrorParser.ESPERA_POR_DEFECTO_S, error.reintentarEnSegundos)
        assertTrue(error.retryable)
    }

    @Test
    fun retryAfter_soloSeAceptanSegundos() {
        assertEquals(17L, parser.parse(429, "", retryAfter = " 17 ").reintentarEnSegundos)
        // Una fecha HTTP no se interpreta (dependería del reloj del teléfono): se usa la espera por defecto.
        assertEquals(60L, parser.parse(429, "", retryAfter = "Wed, 21 Oct 2026 07:28:00 GMT").reintentarEnSegundos)
        assertEquals(60L, parser.parse(429, "", retryAfter = "-5").reintentarEnSegundos)
        assertEquals(30L, parser.parse(503, "", retryAfter = "30").reintentarEnSegundos)
        assertNull("Fuera del 429 no hay espera por defecto", parser.parse(503, "").reintentarEnSegundos)
    }
}
