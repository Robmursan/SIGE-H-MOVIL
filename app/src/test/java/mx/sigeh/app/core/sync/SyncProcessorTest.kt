package mx.sigeh.app.core.sync

import androidx.work.ListenableWorker
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.fechaHoraLocal
import mx.sigeh.app.core.data.ComandoSync
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.data.fake.FakeServidor
import mx.sigeh.app.core.data.fake.FakeSyncRepository
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import mx.sigeh.app.testutil.SyncReceiptDaoFalso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProcessorTest {

    private val reloj = RelojFijo(fechaHoraLocal(2026, 11, 14, 7, 40))
    private val dao = PendingCommandDaoFalso()
    private val recibos = SyncReceiptDaoFalso()
    private val servidor = FakeServidor(reloj)
    private val sesion = SessionStoreEnMemoria(Session("usr-0001", "M. Hernández", "token-de-prueba"))
    private val processor = SyncProcessor(dao, recibos, FakeSyncRepository(servidor), sesion)

    private fun comando(
        id: String,
        programacionId: String,
        estado: EstadoAsistencia,
        servicioId: String = DatosFicticios.medicinaInterna.id,
    ) = PendingCommandEntity(
        clientEventId = id,
        actorId = "usr-0001",
        hospitalId = DatosFicticios.hospitalA.id,
        serviceId = servicioId,
        programacionId = programacionId,
        type = CommandType.ASISTENCIA,
        payloadJson = SigehJson.encodeToString(
            AsistenciaPayload.serializer(),
            AsistenciaPayload(programacionId, estado, "Prueba"),
        ),
        expectedVersion = 3,
        createdAtDevice = reloj.ahora,
        status = CommandStatus.PENDIENTE_LOCAL,
        attemptCount = 0,
        lastErrorCode = null,
    )

    private fun PendingCommandEntity.comoEnvio() = ComandoSync(
        clientEventId, actorId, hospitalId, serviceId, programacionId, type, payloadJson, expectedVersion,
    )

    @Test
    fun primerEnvio_quedaAplicadoConAcuse() = runTest {
        dao.insertar(comando("cmd-lucia", "prog-0003", EstadoAsistencia.PRESENTE))

        assertEquals(SyncOutcome.COMPLETADO, processor.procesar())

        assertEquals(CommandStatus.APLICADO, dao.obtener("cmd-lucia")!!.status)
        assertNotNull(recibos.obtener("cmd-lucia"))
        assertEquals(1, servidor.mutaciones("prog-0003"))
    }

    @Test
    fun reintento_conElMismoClientEventId_noDuplica_yQuedaYaAplicado() = runTest {
        val cmd = comando("cmd-lucia", "prog-0003", EstadoAsistencia.PRESENTE)
        dao.insertar(cmd)
        // Primer envío: el servidor lo aplicó, pero la respuesta nunca llegó al teléfono.
        servidor.recibirLote(listOf(cmd.comoEnvio()))
        assertEquals(1, servidor.mutaciones("prog-0003"))

        // WorkManager reintenta con la misma Idempotency-Key.
        assertEquals(SyncOutcome.COMPLETADO, processor.procesar())

        assertEquals(CommandStatus.YA_APLICADO, dao.obtener("cmd-lucia")!!.status)
        assertEquals("El reenvío no debe duplicar la mutación", 1, servidor.mutaciones("prog-0003"))
        assertEquals(listOf("cmd-lucia", "cmd-lucia"), servidor.clavesRecibidas)
    }

    @Test
    fun capturaTardiaContraRegistroValidado_quedaEnConflictoSinSobrescribir() = runTest {
        // P. Gutiérrez ya fue validada como PRESENTE por Supervisión en el servidor.
        dao.insertar(comando("cmd-paola", "prog-0005", EstadoAsistencia.AUSENTE))

        val resultado = processor.procesar()

        assertEquals(SyncOutcome.COMPLETADO, resultado)
        val local = dao.obtener("cmd-paola")!!
        assertEquals(CommandStatus.CONFLICTO, local.status)
        assertEquals("REGISTRO_VALIDADO", local.lastErrorCode)
        val enServidor = servidor.registros.value["prog-0005"]!!
        assertEquals(EstadoAsistencia.PRESENTE, enServidor.estado)
        assertTrue(enServidor.validado)
    }

    @Test
    fun respuesta401_marcaSesionVencidaYDetieneLaCola() = runTest {
        servidor.simularNoAutorizado = true
        dao.insertar(comando("cmd-lucia", "prog-0003", EstadoAsistencia.PRESENTE))

        assertEquals(SyncOutcome.SESION_VENCIDA, processor.procesar())

        assertTrue(sesion.actual()!!.vencida)
        val local = dao.obtener("cmd-lucia")!!
        assertEquals(CommandStatus.ERROR_REINTENTABLE, local.status)
        assertEquals("HTTP_401", local.lastErrorCode)
        assertEquals(1, servidor.lotesRecibidos)

        // Mientras la sesión siga vencida no se envía nada más (sin reintentos en bucle).
        assertEquals(SyncOutcome.SESION_VENCIDA, processor.procesar())
        assertEquals(1, servidor.lotesRecibidos)
        assertTrue(
            SyncWorker.resultadoPara(SyncOutcome.SESION_VENCIDA, runAttemptCount = 0, RetryPolicy())
                is ListenableWorker.Result.Failure,
        )
    }

    @Test
    fun encargoVencido_quedaDenegadoPorElemento() = runTest {
        dao.insertar(comando("cmd-cg", "prog-0101", EstadoAsistencia.PRESENTE, servicioId = DatosFicticios.cirugiaGeneral.id))

        processor.procesar()

        val local = dao.obtener("cmd-cg")!!
        assertEquals(CommandStatus.DENEGADO, local.status)
        assertEquals("ALCANCE_VENCIDO", local.lastErrorCode)
    }

    @Test
    fun resultadoDelWorker_reintentaConBackoffHastaElLimite() {
        val politica = RetryPolicy(maxIntentos = 3)
        assertTrue(SyncWorker.resultadoPara(SyncOutcome.COMPLETADO, 0, politica) is ListenableWorker.Result.Success)
        assertTrue(SyncWorker.resultadoPara(SyncOutcome.REINTENTAR, 0, politica) is ListenableWorker.Result.Retry)
        assertTrue(SyncWorker.resultadoPara(SyncOutcome.REINTENTAR, 1, politica) is ListenableWorker.Result.Retry)
        assertTrue(SyncWorker.resultadoPara(SyncOutcome.REINTENTAR, 2, politica) is ListenableWorker.Result.Failure)
    }
}
