package mx.sigeh.app.features

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.data.fake.FakeAlcanceRepository
import mx.sigeh.app.core.data.fake.FakeAuthRepository
import mx.sigeh.app.core.data.fake.FakeEtiquetasContexto
import mx.sigeh.app.core.data.fake.FakePaseListaRepository
import mx.sigeh.app.core.data.fake.FakeServidorRepository
import mx.sigeh.app.core.data.fake.FakeSesionesRepository
import mx.sigeh.app.core.database.PurgaLocal
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.features.cuenta.CuentaEvento
import mx.sigeh.app.features.cuenta.CuentaViewModel
import mx.sigeh.app.features.detallejornada.DetalleJornadaViewModel
import mx.sigeh.app.features.login.EstadoServidor
import mx.sigeh.app.features.login.LoginEvento
import mx.sigeh.app.features.login.LoginViewModel
import mx.sigeh.app.features.paselista.PaseListaEvento
import mx.sigeh.app.features.paselista.PaseListaViewModel
import mx.sigeh.app.features.paselista.TipoFila
import mx.sigeh.app.features.selectorambito.SelectorAmbitoEvento
import mx.sigeh.app.features.selectorambito.SelectorAmbitoViewModel
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.IdsSecuenciales
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SchedulerContador
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Punto de control de M2-F4 sin emulador: el recorrido Fake completo con los ViewModels y los
 * repositorios Fake reales (login → servicio → captura → acuse → detalle → cuenta → logout).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecorridoFakeTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val reloj = RelojFijo(HoraHospital.instante("2026-11-20T13:30:00Z")!!)
    private val red = ConectividadFalsa(enLinea = true)
    private val sesiones = SessionStoreEnMemoria(null)
    private val scope = ScopeCache()
    private val paseLista = FakePaseListaRepository(sesiones, reloj)
    private val interruptores = Interruptores(funcionesFuturas = false)
    private val scheduler = SchedulerContador()
    private val colaOffline = PendingCommandDaoFalso()
    private val sesionManager = SesionManager(
        authRepository = FakeAuthRepository(red, IdsSecuenciales()),
        sessionStore = sesiones,
        scopeCache = scope,
        purgaLocal = object : PurgaLocal {
            override suspend fun purgarTodo() = Unit
        },
        pendingCommandDao = colaOffline,
        syncScheduler = scheduler,
        paseListaRepository = paseLista,
        interruptores = interruptores,
    )

    private fun <T> TestScope.eventos(flujo: kotlinx.coroutines.flow.Flow<T>): List<T> {
        val lista = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flujo.toList(lista) }
        return lista
    }

    @Test
    fun recorridoCompleto() = runTest {
        // 9.1 · Login Fake.
        val login = LoginViewModel(sesionManager, FakeServidorRepository(), red)
        val eventosLogin = eventos(login.eventos)
        assertEquals(EstadoServidor.Demostracion, login.uiState.value.servidor)
        login.onIdentificadorChange("cuenta_ficticia")
        login.onContrasenaChange("contrasena-ficticia")
        login.iniciarSesion()
        advanceUntilIdle()
        assertEquals(listOf(LoginEvento.SesionIniciada), eventosLogin)
        assertEquals("cuenta_ficticia", sesiones.actual()!!.identificador)
        assertEquals("La cola offline no se programa sin FUNCIONES_FUTURAS", 0, scheduler.programaciones)

        // 9.2 · Elegir servicio: tres ámbitos Fake, el de división deshabilitado.
        val selector = SelectorAmbitoViewModel(sesiones, scope, FakeEtiquetasContexto(), sesionManager, FakeAlcanceRepository(reloj), interruptores)
        val eventosSelector = eventos(selector.eventos)
        advanceUntilIdle()
        val opciones = selector.uiState.value.opciones
        assertEquals(listOf("Medicina Interna", "Cirugía General", null), opciones.map { it.servicio })
        assertFalse(opciones.last().habilitada)
        assertFalse("Dos opciones válidas: no se preselecciona", selector.uiState.value.puedeContinuar)
        selector.onSeleccionar(opciones.last().clave)
        assertFalse("La tarjeta sin servicio no se puede elegir", selector.uiState.value.puedeContinuar)
        selector.onSeleccionar(opciones.first().clave)
        selector.onContinuar()
        advanceUntilIdle()
        assertEquals(listOf(SelectorAmbitoEvento.Continuar), eventosSelector)
        assertEquals("Medicina Interna", scope.servicioActual()!!.servicioNombre)

        // 9.3-9.5 · Pase, confirmación y acuse.
        val pase = PaseListaViewModel(SavedStateHandle(), scope, paseLista, sesiones, red, reloj, IdsSecuenciales())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pase.uiState.collect {} }
        val eventosPase = eventos(pase.eventos)
        advanceUntilIdle()
        assertEquals("Medicina Interna", pase.uiState.value.servicio)
        assertEquals("Hospital A · America/Mexico_City", pase.uiState.value.subtitulo)
        val jornada = pase.uiState.value.filas.first { it.tipo == TipoFila.CAPTURABLE }
        pase.onAbrirCaptura(jornada.programacionId, EstadoCaptura.PRESENTE)
        pase.onConfirmar(EstadoCaptura.PRESENTE)
        advanceUntilIdle()
        val acuse = eventosPase.filterIsInstance<PaseListaEvento.MostrarAcuse>().single()
        assertTrue(acuse.visuales.message.startsWith("Registrado en el servidor · ${jornada.nombre} · Presente · "))
        val actualizada = pase.uiState.value.filas.single { it.programacionId == jornada.programacionId }
        assertEquals(TipoFila.CAPTURADA, actualizada.tipo)
        assertTrue(actualizada.lineaCaptura!!.startsWith("Capturado por Tú"))
        assertTrue("La captura en línea nunca entra a ColaComandos", colaOffline.filas.value.isEmpty())
        assertEquals("Ni programa el SyncWorker", 0, scheduler.programaciones)

        // 9.7 · Detalle desde la caché y el acuse, sin otro endpoint.
        val detalle = DetalleJornadaViewModel(
            SavedStateHandle(mapOf(DetalleJornadaViewModel.ARG_PROGRAMACION_ID to jornada.programacionId)),
            paseLista,
            scope,
            sesiones,
        )
        val datosDetalle = detalle.uiState.value
        assertTrue(datosDetalle.encontrada)
        assertEquals(EstadoAsistencia.PRESENTE, datosDetalle.estadoOperativo)
        assertEquals("Tú", datosDetalle.acuse!!.single { it.etiqueta == "Capturó" }.valor)
        assertTrue(datosDetalle.acuse.single { it.etiqueta == "Registrado" }.valor.endsWith("(hora del servidor)"))

        // 9.9 · Cuenta y cierre de sesión.
        val cuenta = CuentaViewModel(sesiones, scope, FakeSesionesRepository(reloj), sesionManager, FakeServidorRepository(), paseLista, interruptores)
        val eventosCuenta = eventos(cuenta.eventos)
        advanceUntilIdle()
        assertEquals("cuenta_ficticia", cuenta.uiState.value.identificador)
        assertEquals(3, cuenta.uiState.value.sesiones.size)
        assertTrue(cuenta.uiState.value.sesiones.first().actual)
        assertTrue(cuenta.uiState.value.pie.endsWith("modo demostración"))
        cuenta.onCerrarSesion()
        advanceUntilIdle()
        assertEquals(listOf(CuentaEvento.SesionCerrada), eventosCuenta)
        assertNull(sesiones.actual())
        assertNull("Se purga la caché del pase", paseLista.jornada(jornada.programacionId))
        assertNull(scope.servicioActual())
    }

    @Test
    fun revocarLaSesionDeEsteDispositivo_cierraSesion() = runTest {
        sesionManager.iniciarSesion("cuenta_ficticia", "x")
        val cuenta = CuentaViewModel(sesiones, scope, FakeSesionesRepository(reloj), sesionManager, FakeServidorRepository(), paseLista, interruptores)
        val eventosCuenta = eventos(cuenta.eventos)
        advanceUntilIdle()

        cuenta.onRevocar(DatosFicticios.SESION_ACTUAL_ID)
        advanceUntilIdle()

        assertEquals(listOf(CuentaEvento.SesionCerrada), eventosCuenta)
        assertNull(sesiones.actual())
    }
}
