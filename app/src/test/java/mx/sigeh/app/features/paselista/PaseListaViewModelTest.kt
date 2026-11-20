package mx.sigeh.app.features.paselista

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.MotivoRechazo
import mx.sigeh.app.core.data.ResultadoCaptura
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.IdsSecuenciales
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Pase de lista en línea (prompt M2 §5 y §11): idempotencia, doble toque, conteos y zona horaria. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaseListaViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    /** 07:30 en la Ciudad de México. */
    private val reloj = RelojFijo(HoraHospital.instante("2026-11-20T13:30:00Z")!!)
    private val repo = PaseListaGuionado()
    private val sesion = SessionStoreEnMemoria(Session(DatosFicticios.usuario.id, "M. Hernández", "token-de-prueba"))
    private val red = ConectividadFalsa(enLinea = true)
    private val savedState = SavedStateHandle()

    private fun crear(): PaseListaViewModel {
        val scope = ScopeCache().apply { seleccionarServicio(PaseListaGuionado.SERVICIO) }
        return PaseListaViewModel(savedState, scope, repo, sesion, red, reloj, IdsSecuenciales())
    }

    private fun TestScope.observar(vm: PaseListaViewModel): () -> PaseListaUiState {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return { vm.uiState.value }
    }

    private fun PaseListaUiState.fila(id: String) = filas.single { it.programacionId == id }

    /** Jornada «Jorge Salas», capturable en los datos ficticios. */
    private val capturable = "p-02"

    @Test
    fun carga_nadaMarcadoPorDefectoYConteosEnEstaPagina() = runTest {
        val estado = observar(crear())()

        assertEquals(7, estado.filas.size)
        val porCapturar = estado.filas.filter { it.tipo == TipoFila.CAPTURABLE }
        assertEquals(listOf("p-02", "p-03"), porCapturar.map { it.programacionId })
        assertTrue("Nada se marca por defecto", porCapturar.all { it.estadoOperativo == EstadoAsistencia.PENDIENTE })
        assertTrue(repo.capturas.isEmpty())
        // m = capturables o capturadas (2 + 3); n = capturadas.
        assertEquals(5, estado.capturables)
        assertEquals(3, estado.capturadas)
        assertEquals(
            mapOf(EstadoAsistencia.PRESENTE to 1, EstadoAsistencia.AUSENTE to 1, EstadoAsistencia.PARCIAL to 1, EstadoAsistencia.PENDIENTE to 2),
            estado.conteos,
        )
        assertEquals(listOf("TM", "TV"), estado.turnos.map { it.turnoCodigo })
        assertEquals("Capturado por Tú · 07:04 · validación pendiente", estado.fila("p-01").lineaCaptura)
        assertTrue(estado.fila("p-04").lineaCaptura!!.startsWith("Capturado por Otro usuario"))
        assertEquals("No capturable: programación cancelada", estado.fila("p-06").motivo)
        assertEquals("Se habilita a las 14:30", estado.fila("p-07").motivo)
        assertEquals("TM · 07:00–14:30", estado.fila(capturable).detalle)
        assertEquals(listOf("2026-11-20" to null), repo.consultas)
    }

    @Test
    fun abrirYCancelar_noEnviaNada() = runTest {
        val vm = crear()
        val estado = observar(vm)

        vm.onAbrirCaptura(capturable, EstadoCaptura.AUSENTE)
        assertEquals(EstadoCaptura.AUSENTE, estado().hoja!!.estado)
        assertEquals("TM · 07:00–14:30 · Medicina Interna", estado().hoja!!.detalle)
        vm.onCancelarHoja()

        assertNull(estado().hoja)
        assertTrue(repo.capturas.isEmpty())
    }

    @Test
    fun dobleToque_produceUnaSolaPeticion() = runTest {
        val vm = crear()
        val estado = observar(vm)
        repo.retener = CompletableDeferred()

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)
        vm.onAbrirCaptura(capturable, EstadoCaptura.AUSENTE)

        assertEquals(1, repo.capturas.size)
        assertTrue(estado().hoja!!.enviando)
        assertEquals(EstadoCaptura.PRESENTE, estado().hoja!!.estado)
        assertEquals(TipoFila.ENVIANDO, estado().fila(capturable).tipo)

        repo.retener!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.capturas.size)
        assertNull(estado().hoja)
    }

    @Test
    fun reintentar_reutilizaLaMismaClave() = runTest {
        val vm = crear()
        val estado = observar(vm)
        repo.resultados += ResultadoCaptura.Incierta

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)
        assertEquals(DialogoCaptura.Incierta(capturable, EstadoCaptura.PRESENTE), estado().dialogo)
        vm.onReintentar()
        advanceUntilIdle()

        assertEquals(2, repo.capturas.size)
        assertEquals("Misma intención, misma clave", repo.capturas[0].clave, repo.capturas[1].clave)
        assertEquals(EstadoCaptura.PRESENTE, repo.capturas[1].estado)
    }

    @Test
    fun masTardeYMismoEstado_reutilizaLaClave_otroEstado_esClaveNueva() = runTest {
        val vm = crear()
        observar(vm)
        repo.resultados += ResultadoCaptura.Incierta
        repo.resultados += ResultadoCaptura.Incierta

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)
        vm.onCerrarDialogo() // «Más tarde»
        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)
        vm.onCerrarDialogo()
        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onEstadoHoja(EstadoCaptura.AUSENTE)
        vm.onConfirmar(EstadoCaptura.AUSENTE)
        advanceUntilIdle()

        val claves = repo.capturas.map { it.clave }
        assertEquals(3, claves.size)
        assertEquals("Mismo estado tras «Más tarde»", claves[0], claves[1])
        assertNotEquals("Cambiar de estado es otra intención", claves[1], claves[2])
    }

    @Test
    fun laClaveSobreviveALaMuerteDelProceso() = runTest {
        val primero = crear()
        observar(primero)
        repo.resultados += ResultadoCaptura.Incierta
        primero.onAbrirCaptura(capturable, EstadoCaptura.PARCIAL)
        primero.onConfirmar(EstadoCaptura.PARCIAL)

        // Otro ViewModel con el mismo SavedStateHandle (el sistema restauró el proceso).
        val restaurado = crear()
        observar(restaurado)
        restaurado.onAbrirCaptura(capturable, EstadoCaptura.PARCIAL)
        restaurado.onConfirmar(EstadoCaptura.PARCIAL)
        advanceUntilIdle()

        assertEquals(repo.capturas[0].clave, repo.capturas[1].clave)
    }

    @Test
    fun confirmada_muestraElAcuseRecargaYDescartaLaClave() = runTest {
        val vm = crear()
        val estado = observar(vm)
        val eventos = mutableListOf<PaseListaEvento>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.toList(eventos) }

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)

        val acuse = eventos.single() as PaseListaEvento.MostrarAcuse
        assertEquals("Registrado en el servidor · Jorge Salas · Presente · 07:05:12", acuse.visuales.message)
        assertEquals(capturable, acuse.programacionId)
        assertEquals("Tras el 200 se vuelve a pedir la lista", 2, repo.consultas.size)
        assertTrue(estado().fila(capturable).resaltada)
        assertEquals("[]", savedState.get<String>(PaseListaViewModel.CLAVE_INTENCIONES))
        assertNull(estado().hoja)
    }

    @Test
    fun yaRegistrada_avisaRecargaYDescartaLaClave() = runTest {
        val vm = crear()
        val estado = observar(vm)
        repo.resultados += ResultadoCaptura.Rechazada(MotivoRechazo.YA_REGISTRADA)

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)

        assertEquals(DialogoCaptura.YaRegistrada(capturable), estado().dialogo)
        assertEquals(2, repo.consultas.size)
        assertEquals("[]", savedState.get<String>(PaseListaViewModel.CLAVE_INTENCIONES))
    }

    @Test
    fun sinRed_laCapturaNoSeAbreNiSeEnvia() = runTest {
        val vm = crear()
        val estado = observar(vm)
        red.enLinea.value = false

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)

        assertEquals(false, estado().enLinea)
        assertNull(estado().hoja)
        assertTrue(repo.capturas.isEmpty())
    }

    @Test
    fun zonaDistintaDelServidor_recalculaHoyYRepiteLaConsulta() = runTest {
        // 21:30 del 20 nov en la Ciudad de México, pero ya 21 nov en UTC.
        reloj.ahora = HoraHospital.instante("2026-11-21T03:30:00Z")!!
        repo.zonaRespuesta = "UTC"

        val estado = observar(crear())()

        assertEquals(listOf("2026-11-20" to null, "2026-11-21" to null), repo.consultas)
        assertEquals("2026-11-21", estado.fecha)
        assertTrue(estado.subtitulo.endsWith("UTC"))
    }

    @Test
    fun turno_consultaConHorarioYConservaLosChips() = runTest {
        val vm = crear()
        val estado = observar(vm)

        vm.onTurnoSeleccionado(DatosFicticios.HORARIO_TV_ID)

        assertEquals("2026-11-20" to DatosFicticios.HORARIO_TV_ID, repo.consultas.last())
        assertEquals(listOf("Héctor Ruiz"), estado().filas.map { it.nombre })
        assertEquals(listOf("TM", "TV"), estado().turnos.map { it.turnoCodigo })
    }

    @Test
    fun sesionTerminada_marcaLaSesionVencida() = runTest {
        val vm = crear()
        val estado = observar(vm)
        repo.resultados += ResultadoCaptura.SesionTerminada

        vm.onAbrirCaptura(capturable, EstadoCaptura.PRESENTE)
        vm.onConfirmar(EstadoCaptura.PRESENTE)

        assertEquals(BloqueoPase.SESION_TERMINADA, estado().bloqueo)
        assertTrue(sesion.actual()!!.vencida)
    }
}
