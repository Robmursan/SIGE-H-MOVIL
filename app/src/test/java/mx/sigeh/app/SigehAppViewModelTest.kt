package mx.sigeh.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.common.fechaHoraLocal
import mx.sigeh.app.core.data.ResultadoConsulta
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.data.fake.FakeAuthRepository
import mx.sigeh.app.core.data.fake.FakePaseListaRepository
import mx.sigeh.app.core.database.PurgaLocal
import mx.sigeh.app.core.model.AmbitoActivo
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.core.network.NetworkConfig
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.IdsSecuenciales
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SchedulerContador
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Ajustes de M2 a la sesión global (prompt §7.2): la cola offline solo se programa con
 * `FUNCIONES_FUTURAS` y la purga por alcance vencido solo existe en modo Fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SigehAppViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val reloj = RelojFijo(fechaHoraLocal(2026, 11, 14, 8, 0))
    private val scheduler = SchedulerContador()
    private val scope = ScopeCache()
    private var purgas = 0
    private val sesiones = SessionStoreEnMemoria(
        Session(
            usuarioId = DatosFicticios.usuario.id,
            nombreCorto = DatosFicticios.usuario.nombreCorto,
            accessToken = "m0-ficticio",
            asignaciones = DatosFicticios.asignaciones,
        ),
    )
    private val paseLista = FakePaseListaRepository(sesiones, reloj)

    private fun sesionManager(futuras: Boolean) = SesionManager(
        authRepository = FakeAuthRepository(ConectividadFalsa(enLinea = true), IdsSecuenciales()),
        sessionStore = sesiones,
        scopeCache = scope,
        purgaLocal = object : PurgaLocal {
            override suspend fun purgarTodo() {
                purgas++
            }
        },
        pendingCommandDao = PendingCommandDaoFalso(),
        syncScheduler = scheduler,
        paseListaRepository = paseLista,
        interruptores = Interruptores(funcionesFuturas = futuras),
    )

    private fun crearViewModel(futuras: Boolean, iamReal: Boolean) = SigehAppViewModel(
        sessionStore = sesiones,
        scopeCache = scope,
        sesionManager = sesionManager(futuras),
        reloj = reloj,
        syncScheduler = scheduler,
        interruptores = Interruptores(funcionesFuturas = futuras),
        networkConfig = NetworkConfig(if (iamReal) "http://10.0.2.2:3000/api/v1" else "", iamRealSolicitado = iamReal),
    )

    /** Encargo de Cirugía General que venció a las 23:59 del día anterior. */
    private fun seleccionarAlcanceVencido() {
        val ayer = fechaHoraLocal(2026, 11, 13, 8, 0)
        val alcance = DatosFicticios.alcances(ayer).first { it.rol == RolAlcance.ENCARGADO }
        scope.seleccionar(AmbitoActivo(alcance, DatosFicticios.matutino))
    }

    @Test
    fun sinFuncionesFuturas_noSeProgramaLaColaOffline() {
        crearViewModel(futuras = false, iamReal = false)

        assertEquals(0, scheduler.programaciones)
    }

    @Test
    fun conFuncionesFuturas_seReanudaLaColaAlAbrirConSesion() {
        crearViewModel(futuras = true, iamReal = false)

        assertEquals(1, scheduler.programaciones)
    }

    @Test
    fun modoFake_purgaCuandoVenceElAlcance() {
        seleccionarAlcanceVencido()

        crearViewModel(futuras = false, iamReal = false)

        assertEquals(1, purgas)
        assertNull(scope.ambitoActual())
    }

    @Test
    fun iamReal_noPurgaPorVigencia_laDecideElServidorCon403() {
        seleccionarAlcanceVencido()

        crearViewModel(futuras = false, iamReal = true)

        assertEquals(0, purgas)
        assertNotNull(scope.ambitoActual())
    }

    @Test
    fun login_programaLaColaSoloConFuncionesFuturas() = runTest {
        sesionManager(futuras = false).iniciarSesion("cuenta_ficticia", "x")
        assertEquals(0, scheduler.programaciones)

        sesionManager(futuras = true).iniciarSesion("cuenta_ficticia", "x")
        assertEquals(1, scheduler.programaciones)
    }

    @Test
    fun cerrarSesion_purgaLaCacheDelPase() = runTest {
        val consulta = paseLista.consultar(DatosFicticios.hospitalA.id, DatosFicticios.medicinaInterna.id, "2026-11-14")
        val id = (consulta as ResultadoConsulta.Exito).pagina.jornadas.first().programacionId
        assertNotNull(paseLista.jornada(id))

        sesionManager(futuras = false).cerrarSesion()

        assertNull(paseLista.jornada(id))
        assertNull(sesiones.actual())
        assertTrue(purgas > 0)
    }
}
