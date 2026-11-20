package mx.sigeh.app.features.selectorambito

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.AsignacionSesion
import mx.sigeh.app.core.auth.CodigosPermiso
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.EtiquetasPorIdentificador
import mx.sigeh.app.core.data.fake.FakeAlcanceRepository
import mx.sigeh.app.core.data.fake.FakeAuthRepository
import mx.sigeh.app.core.database.PurgaLocal
import mx.sigeh.app.core.designsystem.components.SigehTab
import mx.sigeh.app.core.model.RolAlcance
import mx.sigeh.app.navigation.pestanasVisibles
import mx.sigeh.app.testutil.ConectividadFalsa
import mx.sigeh.app.testutil.IdsSecuenciales
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.PendingCommandDaoFalso
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SchedulerContador
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectorYNavegacionTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun unaSolaOpcionValida_sePreseleccionaYUsaEtiquetasHonestas() = runTest {
        val servicio = "6a1f4e2c-0000-4000-8000-00000000c91e"
        val sesiones = SessionStoreEnMemoria(
            Session(
                usuarioId = "u", nombreCorto = "cuenta", accessToken = "t",
                asignaciones = listOf(
                    AsignacionSesion(RolAlcance.ENCARGADO, "6a1f4e2c-0000-4000-8000-0000000000b1", "6a1f4e2c-0000-4000-8000-0000000000c1", servicio, listOf(CodigosPermiso.ASISTENCIA_CAPTURAR)),
                    AsignacionSesion(RolAlcance.TITULAR, "6a1f4e2c-0000-4000-8000-0000000000b1", null, null, listOf(CodigosPermiso.ASISTENCIA_VALIDAR)),
                ),
            ),
        )
        val scope = ScopeCache()
        val manager = SesionManager(
            FakeAuthRepository(ConectividadFalsa(true), IdsSecuenciales()), sesiones, scope,
            object : PurgaLocal {
                override suspend fun purgarTodo() = Unit
            },
            PendingCommandDaoFalso(), SchedulerContador(), PaseListaGuionado(), Interruptores(false),
        )
        val vm = SelectorAmbitoViewModel(sesiones, scope, EtiquetasPorIdentificador(), manager, FakeAlcanceRepository(RelojFijo(0)), Interruptores(false))
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado.puedeContinuar)
        val elegida = estado.opciones.single { it.seleccionada }
        assertEquals("Servicio ··· c91e", elegida.servicio)
        assertEquals("Hospital ··· 00b1", elegida.hospital)
        assertTrue(elegida.capturar)
        assertEquals("Sin ASISTENCIA_VALIDAR no hay chip «Consultar»", false, elegida.consultar)
        vm.onContinuar()
        advanceUntilIdle()
        assertEquals(servicio, scope.servicioActual()!!.servicioId)
        assertEquals(RolAlcance.ENCARGADO, scope.servicioActual()!!.rol)
    }

    @Test
    fun pestanas_programacionSoloConPermiso_yFuturasSoloConInterruptor() {
        assertEquals(listOf(SigehTab.PASE, SigehTab.CUENTA), pestanasVisibles(funcionesFuturas = false, programacion = false))
        assertEquals(
            listOf(SigehTab.PASE, SigehTab.PROGRAMACION, SigehTab.CUENTA),
            pestanasVisibles(funcionesFuturas = false, programacion = true),
        )
        assertEquals(
            listOf(SigehTab.PASE, SigehTab.INCIDENCIAS, SigehTab.MOVIMIENTOS, SigehTab.PROGRAMACION, SigehTab.CUENTA),
            pestanasVisibles(funcionesFuturas = true, programacion = true),
        )
    }
}
