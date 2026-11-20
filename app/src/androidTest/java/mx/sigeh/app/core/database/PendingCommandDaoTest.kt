package mx.sigeh.app.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingCommandDaoTest {

    private lateinit var database: SigehDatabase
    private lateinit var dao: PendingCommandDao

    @Before
    fun crearBase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SigehDatabase::class.java).build()
        dao = database.pendingCommandDao()
    }

    @After
    fun cerrarBase() = database.close()

    private fun comando(id: String, programacionId: String, creado: Long, estado: CommandStatus = CommandStatus.PENDIENTE_LOCAL) =
        PendingCommandEntity(
            clientEventId = id,
            actorId = "usr-0001",
            hospitalId = "hosp-a",
            serviceId = "svc-mi",
            programacionId = programacionId,
            type = CommandType.ASISTENCIA,
            payloadJson = """{"programacionId":"$programacionId","estado":"PRESENTE","etiqueta":"Prueba"}""",
            expectedVersion = 3,
            createdAtDevice = creado,
            status = estado,
            attemptCount = 0,
            lastErrorCode = null,
        )

    @Test
    fun insertar_yObtenerPorId() = runTest {
        val cmd = comando("cmd-1", "prog-0001", 1_000)
        dao.insertar(cmd)

        assertEquals(cmd, dao.obtener("cmd-1"))
        assertEquals(cmd, dao.ultimoPara("prog-0001", CommandType.ASISTENCIA))
        assertNull(dao.obtener("no-existe"))
    }

    @Test
    fun actualizarEstado_cambiaEstadoYCodigo() = runTest {
        dao.insertar(comando("cmd-1", "prog-0001", 1_000))

        dao.actualizarEstado("cmd-1", CommandStatus.CONFLICTO, "REGISTRO_VALIDADO")

        val guardado = dao.obtener("cmd-1")!!
        assertEquals(CommandStatus.CONFLICTO, guardado.status)
        assertEquals("REGISTRO_VALIDADO", guardado.lastErrorCode)
    }

    @Test
    fun consultaPorEstado_devuelveSoloLosEstadosPedidosEnOrdenDeCreacion() = runTest {
        dao.insertar(comando("cmd-b", "prog-0002", 2_000))
        dao.insertar(comando("cmd-a", "prog-0001", 1_000))
        dao.insertar(comando("cmd-c", "prog-0003", 3_000, CommandStatus.APLICADO))
        dao.insertar(comando("cmd-d", "prog-0004", 4_000, CommandStatus.CONFLICTO))

        val pendientes = dao.obtenerPorEstado(listOf(CommandStatus.PENDIENTE_LOCAL))
        assertEquals(listOf("cmd-a", "cmd-b"), pendientes.map { it.clientEventId })

        val conteo = dao.observarConteo(listOf(CommandStatus.PENDIENTE_LOCAL, CommandStatus.CONFLICTO)).first()
        assertEquals(3, conteo)
        assertEquals(1, dao.observarPorEstado(listOf(CommandStatus.APLICADO)).first().size)
    }

    @Test
    fun correccion_soloSeAplicaSiElComandoNuncaSeEnvio() = runTest {
        dao.insertar(comando("cmd-1", "prog-0001", 1_000))

        assertEquals(1, dao.actualizarPayloadSiNoEnviado("cmd-1", "{\"v\":2}"))

        dao.marcarEnviando(listOf("cmd-1"))
        val enviado = dao.obtener("cmd-1")!!
        assertEquals(CommandStatus.ENVIANDO, enviado.status)
        assertEquals(1, enviado.attemptCount)
        assertEquals(0, dao.actualizarPayloadSiNoEnviado("cmd-1", "{\"v\":3}"))
        assertEquals("{\"v\":2}", dao.obtener("cmd-1")!!.payloadJson)
    }
}
