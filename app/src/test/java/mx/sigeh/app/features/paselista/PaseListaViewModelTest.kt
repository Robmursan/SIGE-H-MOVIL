package mx.sigeh.app.features.paselista

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.fechaHoraLocal
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.data.fake.FakeDataSeeder
import mx.sigeh.app.core.data.fake.FakePaseListaOfflineRepository
import mx.sigeh.app.core.data.fake.FakeServidor
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.model.AmbitoActivo
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.SigehJson
import mx.sigeh.app.core.sync.ColaComandos
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.IdsSecuenciales
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SchedulerContador
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import mx.sigeh.app.testutil.SyncReceiptDaoFalso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaseListaViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val dao = PendingCommandDaoFalso()
    private val scheduler = SchedulerContador()
    private val reloj = RelojFijo(fechaHoraLocal(2026, 11, 14, 7, 30))

    private fun crearViewModel(): PaseListaViewModel {
        val servidor = FakeServidor(reloj)
        val repositorio = FakePaseListaOfflineRepository(servidor, FakeDataSeeder(dao, SyncReceiptDaoFalso(), scheduler))
        val cola = ColaComandos(dao, SigehJson, reloj, IdsSecuenciales(), scheduler)
        val scope = ScopeCache().apply {
            seleccionar(AmbitoActivo(DatosFicticios.alcances(reloj.ahora).first(), DatosFicticios.matutino))
        }
        val sesion = SessionStoreEnMemoria(Session("usr-0001", "M. Hernández", "token-de-prueba"))
        return PaseListaViewModel(scope, repositorio, cola, sesion, reloj, ConectividadFalsa(enLinea = false))
    }

    private suspend fun TestScope.iniciar(vm: PaseListaViewModel): PaseListaUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm.uiState.first { !it.cargando }
    }

    private fun comandosDe(programacionId: String): List<PendingCommandEntity> =
        dao.filas.value.filter { it.programacionId == programacionId && it.type == mx.sigeh.app.core.model.CommandType.ASISTENCIA }

    private fun estadoDe(comando: PendingCommandEntity): EstadoAsistencia =
        SigehJson.decodeFromString(AsistenciaPayload.serializer(), comando.payloadJson).estado

    @Test
    fun contadores_exigiblesExcluyeANoExigibles() = runTest {
        val estado = iniciar(crearViewModel())

        with(estado.contadores) {
            assertEquals(8, programados)
            assertEquals(7, exigibles)
            assertEquals(4, capturados)
            assertEquals(3, pendientes)
            assertEquals(0, validados)
        }
        val marta = estado.filas.single { it.nombre == "Marta Peña O." }
        assertFalse(marta.exigible)
        assertEquals(EstadoAsistencia.NO_EXIGIBLE, marta.estado)
    }

    @Test
    fun sinCaptura_quedaPendienteYNuncaAusentePorOmision() = runTest {
        val estado = iniciar(crearViewModel())

        val lucia = estado.filas.single { it.programacionId == "prog-0003" }
        assertEquals(EstadoAsistencia.PENDIENTE, lucia.estado)
        assertEquals(IndicadorFila.SIN_CAPTURAR, lucia.indicador)
        assertEquals(1, estado.filas.count { it.estado == EstadoAsistencia.AUSENTE }) // solo la captura explícita de P. Gutiérrez
    }

    @Test
    fun cambioDeEstado_creaComandoPendienteLocal() = runTest {
        val vm = crearViewModel()
        iniciar(vm)
        val programacionesAntes = scheduler.programaciones

        vm.onEstadoSeleccionado("prog-0003", EstadoAsistencia.PRESENTE)
        advanceUntilIdle()

        val comando = comandosDe("prog-0003").single()
        assertEquals(CommandStatus.PENDIENTE_LOCAL, comando.status)
        assertEquals(0, comando.attemptCount)
        assertEquals(EstadoAsistencia.PRESENTE, estadoDe(comando))
        assertEquals("usr-0001", comando.actorId)
        assertEquals(DatosFicticios.medicinaInterna.id, comando.serviceId)
        assertTrue("Debe programar la sincronización", scheduler.programaciones > programacionesAntes)

        val fila = vm.uiState.value.filas.single { it.programacionId == "prog-0003" }
        assertEquals(EstadoAsistencia.PRESENTE, fila.estado)
        assertEquals(IndicadorFila.EN_TELEFONO, fila.indicador)
        assertEquals(5, vm.uiState.value.contadores.capturados)
    }

    @Test
    fun correccionDeLaMismaFila_reutilizaElMismoClientEventId() = runTest {
        val vm = crearViewModel()
        iniciar(vm)

        vm.onEstadoSeleccionado("prog-0003", EstadoAsistencia.PRESENTE)
        advanceUntilIdle()
        val original = comandosDe("prog-0003").single().clientEventId

        vm.onEstadoSeleccionado("prog-0003", EstadoAsistencia.AUSENTE)
        advanceUntilIdle()

        val comandos = comandosDe("prog-0003")
        assertEquals("La corrección no debe crear otro comando", 1, comandos.size)
        assertEquals(original, comandos.single().clientEventId)
        assertEquals(EstadoAsistencia.AUSENTE, estadoDe(comandos.single()))
    }

    @Test
    fun correccionTrasUnIntentoDeEnvio_creaUnComandoNuevo() = runTest {
        val vm = crearViewModel()
        iniciar(vm)
        vm.onEstadoSeleccionado("prog-0003", EstadoAsistencia.PRESENTE)
        advanceUntilIdle()
        val original = comandosDe("prog-0003").single()
        // El comando ya salió del teléfono una vez: su Idempotency-Key no puede cambiar de contenido.
        dao.marcarEnviando(listOf(original.clientEventId))
        dao.actualizarEstado(original.clientEventId, CommandStatus.ERROR_REINTENTABLE, "RED_NO_DISPONIBLE")
        reloj.ahora += 60_000

        vm.onEstadoSeleccionado("prog-0003", EstadoAsistencia.PARCIAL)
        advanceUntilIdle()

        val comandos = comandosDe("prog-0003")
        assertEquals(2, comandos.size)
        assertEquals(EstadoAsistencia.PRESENTE, estadoDe(comandos.single { it.clientEventId == original.clientEventId }))
        val nuevo = comandos.single { it.clientEventId != original.clientEventId }
        assertNotEquals(original.clientEventId, nuevo.clientEventId)
        assertEquals(EstadoAsistencia.PARCIAL, estadoDe(nuevo))
    }
}
