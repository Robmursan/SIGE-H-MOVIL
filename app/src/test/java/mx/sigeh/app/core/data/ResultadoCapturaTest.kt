package mx.sigeh.app.core.data

import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.model.ValidacionAdministrativa
import mx.sigeh.app.core.network.ApiErrorParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Traducción de errores HTTP de la captura (prompt M2 §6.6) y ciclo de vida de la `Idempotency-Key`. */
class ResultadoCapturaTest {

    private val parser = ApiErrorParser(SigehJson)

    private fun resultado(codigo: Int, mensaje: String, error: String = "Error"): ResultadoCaptura =
        parser.parse(codigo, """{"statusCode":$codigo,"message":"$mensaje","error":"$error"}""").aResultadoCaptura()

    @Test
    fun conflictoConCodigoDesconocido_esNoAdmiteCaptura() {
        assertEquals(ResultadoCaptura.Rechazada(MotivoRechazo.NO_ADMITE_CAPTURA), resultado(409, "MOTIVO_NUEVO", "Conflict"))
    }

    @Test
    fun iamNoDisponible503_esIncierta() {
        // Mismo texto para IAM apagado o persistencia caída: se ofrece reintentar con la misma clave.
        assertEquals(ResultadoCaptura.Incierta, resultado(503, "IAM no disponible", "Service Unavailable"))
    }

    @Test
    fun cuerpoIlegible_seDecideSoloPorCodigo() {
        assertEquals(ResultadoCaptura.Rechazada(MotivoRechazo.NO_ADMITE_CAPTURA), parser.parse(409, "<html/>").aResultadoCaptura())
        assertEquals(ResultadoCaptura.Incierta, parser.parse(503, "").aResultadoCaptura())
        assertEquals(ResultadoCaptura.Incierta, parser.parse(502, "").aResultadoCaptura())
        assertEquals(ResultadoCaptura.SinPermiso, parser.parse(403, "").aResultadoCaptura())
    }

    @Test
    fun soloLasRespuestasDefinitivasDescartanLaClave() {
        val acuse = AcuseCaptura(
            programacionId = "p",
            asistenciaId = "a",
            eventoId = "9007199254740993",
            estado = EstadoCaptura.PRESENTE,
            validacion = ValidacionAdministrativa.PENDIENTE,
            actorId = "u",
            registradoEn = "2026-11-20T07:05:00.000-06:00",
            revision = "1",
        )
        // 200, 400, 403, 404 y 409.
        listOf(
            ResultadoCaptura.Confirmada(acuse),
            ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA),
            ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA),
            ResultadoCaptura.SinPermiso,
            ResultadoCaptura.NoEncontrada,
        ).forEach { assertTrue("$it es terminal", it.esTerminal) }

        // Timeout/red/503 y 401: la clave se conserva para reintentar la misma intención.
        listOf(
            ResultadoCaptura.Incierta,
            ResultadoCaptura.NoHabilitada,
            ResultadoCaptura.SesionTerminada,
        ).forEach { assertFalse("$it conserva la clave", it.esTerminal) }
    }
}
