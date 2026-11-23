package mx.sigeh.app.features.selectorambito

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.AsignacionSesion
import mx.sigeh.app.core.auth.CodigosPermiso
import mx.sigeh.app.core.auth.ContextoPropio
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.SesionManager
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.Interruptores
import mx.sigeh.app.core.data.ContextosRepository
import mx.sigeh.app.core.data.EtiquetasPorIdentificador
import mx.sigeh.app.core.data.ResultadoContextos
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectorYNavegacionTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val servicio = "6a1f4e2c-0000-4000-8000-00000000c91e"
    private val hospital = "6a1f4e2c-0000-4000-8000-0000000000b1"
    private val division = "6a1f4e2c-0000-4000-8000-0000000000c1"

    private val sesiones = SessionStoreEnMemoria(
        Session(
            usuarioId = "u", nombreCorto = "cuenta", accessToken = "t",
            asignaciones = listOf(
                AsignacionSesion(RolAlcance.ENCARGADO, hospital, division, servicio, listOf(CodigosPermiso.ASISTENCIA_CAPTURAR)),
                AsignacionSesion(RolAlcance.TITULAR, hospital, null, null, listOf(CodigosPermiso.ASISTENCIA_VALIDAR)),
            ),
        ),
    )
    private val scope = ScopeCache()

    private fun contextosQueResponden(resultado: ResultadoContextos) = object : ContextosRepository {
        override suspend fun contextosPropios(): ResultadoContextos = resultado
    }

    private fun selector(contextos: ContextosRepository): SelectorAmbitoViewModel {
        val manager = SesionManager(
            FakeAuthRepository(ConectividadFalsa(true), IdsSecuenciales()), sesiones, scope,
            object : PurgaLocal {
                override suspend fun purgarTodo() = Unit
            },
            PendingCommandDaoFalso(), SchedulerContador(), PaseListaGuionado(), Interruptores(false),
        )
        return SelectorAmbitoViewModel(
            sesiones, scope, contextos, EtiquetasPorIdentificador(), manager, FakeAlcanceRepository(RelojFijo(0)), Interruptores(false),
        )
    }

    @Test
    fun contextosNoDisponibles_respaldoConAsignacionesYEtiquetasHonestas() = runTest {
        val vm = selector(contextosQueResponden(ResultadoContextos.NoDisponible(503)))
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado.puedeContinuar)
        val elegida = estado.opciones.single { it.seleccionada }
        assertEquals("Servicio ··· c91e", elegida.servicio)
        assertEquals("Hospital ··· 00b1", elegida.hospital)
        assertTrue(elegida.capturar)
        assertEquals("Sin ASISTENCIA_VALIDAR no hay chip «Consultar»", false, elegida.consultar)
        assertNull("/auth/me no informa vigencias", elegida.vigencia)
        vm.onContinuar()
        advanceUntilIdle()
        assertEquals(servicio, scope.servicioActual()!!.servicioId)
        assertEquals(RolAlcance.ENCARGADO, scope.servicioActual()!!.rol)
        assertNull(scope.servicioActual()!!.zonaHoraria)
    }

    @Test
    fun sinConexion_tambienUsaElRespaldo() = runTest {
        val vm = selector(contextosQueResponden(ResultadoContextos.SinConexion))
        advanceUntilIdle()

        assertEquals(listOf("Servicio ··· c91e", null), vm.uiState.value.opciones.map { it.servicio })
    }

    @Test
    fun contextosDisponibles_nombresDelServidorVigenciaYZonaDelHospital() = runTest {
        val contextos = listOf(
            ContextoPropio(
                assignmentId = "a1", hospitalId = hospital, hospitalNombre = "Hospital Sintético A",
                zonaHoraria = "America/Tijuana", divisionId = division, divisionNombre = "División Sintética",
                servicioId = servicio, servicioNombre = "Servicio Sintético", rol = RolAlcance.ENCARGADO,
                vigenteDesde = "2026-11-15T08:00:00.000Z", vigenteHasta = "2026-12-01T08:00:00.000Z",
                permisos = listOf(CodigosPermiso.ASISTENCIA_CAPTURAR),
            ),
        )
        val vm = selector(contextosQueResponden(ResultadoContextos.Exito(contextos)))
        advanceUntilIdle()

        val elegida = vm.uiState.value.opciones.single()
        assertTrue("Única opción válida: se preselecciona", elegida.seleccionada)
        assertEquals("Hospital Sintético A", elegida.hospital)
        assertEquals("División Sintética", elegida.division)
        assertEquals("Servicio Sintético", elegida.servicio)
        assertEquals("Vigente hasta 1 dic 00:00", elegida.vigencia)

        vm.onContinuar()
        advanceUntilIdle()
        val activo = scope.servicioActual()!!
        assertEquals("Servicio Sintético", activo.servicioNombre)
        assertEquals("Hospital Sintético A", activo.hospitalNombre)
        assertEquals("America/Tijuana", activo.zonaHoraria)
        assertEquals("2026-12-01T08:00:00.000Z", activo.vigenteHasta)
    }

    @Test
    fun contextosVacios_noSeUsaElRespaldo() = runTest {
        val vm = selector(contextosQueResponden(ResultadoContextos.Exito(emptyList())))
        advanceUntilIdle()

        assertTrue("El servidor dice que no hay contextos vigentes: no se ofrece nada", vm.uiState.value.sinAmbitos)
    }

    @Test
    fun contextos401_marcaLaSesionVencidaYNoOfreceNada() = runTest {
        val vm = selector(contextosQueResponden(ResultadoContextos.SesionVencida))
        advanceUntilIdle()

        assertTrue(sesiones.actual()!!.vencida)
        assertTrue(vm.uiState.value.sinAmbitos)
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
