package mx.sigeh.app.core.network.asistencia

import kotlinx.serialization.builtins.ListSerializer
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.MotivoNoCapturable
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.model.ValidacionAdministrativa
import mx.sigeh.app.core.network.iam.SessionDto
import mx.sigeh.app.testutil.ContratoAsistencia
import mx.sigeh.app.testutil.ContratoIam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Serialización de los DTO de asistencia y sesiones con JSON de la forma exacta del contrato `sigeh-v1`. */
class AsistenciaDtosTest {

    private fun pagina(json: String = ContratoAsistencia.PAGINA) =
        SigehJson.decodeFromString(PaseListaPageDto.serializer(), json)

    @Test
    fun pagina_seLeeCompletaConSuPaginacion() {
        val pagina = pagina()

        assertEquals(ContratoIam.HOSPITAL_ID, pagina.hospitalId)
        assertEquals(ContratoIam.SERVICIO_ID, pagina.servicioId)
        assertEquals(ContratoAsistencia.FECHA, pagina.fecha)
        assertEquals(ContratoAsistencia.ZONA, pagina.zonaHoraria)
        assertEquals(3, pagina.items.size)
        assertEquals(ContratoAsistencia.CURSOR, pagina.nextCursor)
        assertEquals(100, pagina.limit)
        assertEquals("id:asc", pagina.order)
    }

    @Test
    fun itemCapturado_conservaEventoIdGrandeYRevisionComoTexto() {
        val item = pagina().items[0]

        assertTrue(item.capturada)
        assertFalse(item.capturable)
        assertEquals(MotivoNoCapturable.YA_REGISTRADA, item.motivoNoCapturable)
        assertEquals(EstadoAsistencia.PRESENTE, item.estadoOperativo)
        assertEquals(ValidacionAdministrativa.PENDIENTE, item.validacionAdministrativa)
        assertEquals("1", item.revision)
        val captura = item.ultimaCaptura!!
        assertEquals("Sin pérdida de precisión", "9007199254740993", captura.eventoId)
        assertEquals(ContratoIam.USUARIO_ID, captura.actorId)
        assertEquals("2026-11-20T07:04:12.345-06:00", captura.registradoEn)
        assertEquals("Ana Sofía Ficticia", item.empleado.nombreVisible)
    }

    @Test
    fun itemPorCapturar_aceptaNulosExplicitos() {
        val item = pagina().items[1]

        assertTrue(item.capturable)
        assertFalse(item.capturada)
        assertEquals(EstadoAsistencia.PENDIENTE, item.estadoOperativo)
        assertEquals("0", item.revision)
        assertNull(item.asistenciaId)
        assertNull(item.validacionAdministrativa)
        assertNull(item.ultimaCaptura)
        assertNull(item.motivoNoCapturable)
        assertEquals(ContratoAsistencia.HORARIO_TM, item.horarioId)
        assertEquals("TM", item.turnoCodigo)
    }

    @Test
    fun itemCancelado_aceptaNulosOmitidos() {
        val item = pagina().items[2]

        assertEquals(EstadoProgramacion.CANCELADA, item.estadoProgramacion)
        assertEquals(EstadoAsistencia.NO_EXIGIBLE, item.estadoOperativo)
        assertEquals(MotivoNoCapturable.PROGRAMACION_NO_CAPTURABLE, item.motivoNoCapturable)
        assertNull(item.horarioId)
        assertNull(item.turnoCodigo)
        assertNull(item.asistenciaId)
        assertNull(item.ultimaCaptura)
        assertEquals("2026-11-20T14:30:00.000-06:00", item.inicioProgramado)
    }

    @Test
    fun paginaVacia_esUnaListaSinCursor() {
        val pagina = pagina(ContratoAsistencia.PAGINA_VACIA)

        assertTrue(pagina.items.isEmpty())
        assertNull(pagina.nextCursor)
    }

    @Test
    fun acuse_conservaEventoIdGrandeYSeConvierteAlDominio() {
        val recibo = SigehJson.decodeFromString(CaptureReceiptDto.serializer(), ContratoAsistencia.acuse("AUSENTE"))

        assertEquals(ContratoAsistencia.EVENTO_GRANDE, recibo.eventoId)
        assertEquals("1", recibo.revision)
        val acuse = recibo.aAcuse()
        assertEquals(ContratoAsistencia.PROGRAMACION_PENDIENTE, acuse.programacionId)
        assertEquals(ContratoAsistencia.ASISTENCIA_ID, acuse.asistenciaId)
        assertEquals(ContratoAsistencia.EVENTO_GRANDE, acuse.eventoId)
        assertEquals(EstadoCaptura.AUSENTE, acuse.estado)
        assertEquals(ValidacionAdministrativa.PENDIENTE, acuse.validacion)
        assertEquals(ContratoIam.USUARIO_ID, acuse.actorId)
        assertEquals("2026-11-20T07:05:00.000-06:00", acuse.registradoEn)
        assertEquals("1", acuse.revision)
    }

    @Test
    fun cuerpoDeCaptura_esExactamenteElDelContrato() {
        EstadoCaptura.entries.forEach { estado ->
            assertEquals(
                """{"estado":"${estado.name}","expectedRevision":"0"}""",
                SigehJson.encodeToString(CapturarAsistenciaDto.serializer(), CapturarAsistenciaDto.primeraCaptura(estado)),
            )
        }
    }

    @Test
    fun sesiones_aceptanRevocacionNulaOmitidaOConFecha() {
        val sesiones = SigehJson.decodeFromString(ListSerializer(SessionDto.serializer()), ContratoIam.SESIONES)

        assertEquals(3, sesiones.size)
        assertEquals(listOf(true, false, false), sesiones.map { it.current })
        assertEquals(ContratoIam.SESION_ID, sesiones[0].sessionId)
        assertNull(sesiones[0].revokedAt)
        assertEquals("2026-11-19T14:00:00.000Z", sesiones[1].revokedAt)
        assertNull(sesiones[2].revokedAt)
        assertEquals("2026-11-20T13:30:00.000Z", sesiones[0].idleExpiresAt)
    }
}
