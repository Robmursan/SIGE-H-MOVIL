package mx.sigeh.app.features.programacion

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.ScopeCache
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.ProgramacionRepository
import mx.sigeh.app.core.data.ResultadoProgramacion
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.data.fake.programacionDelDia
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.testutil.MainDispatcherRule
import mx.sigeh.app.testutil.PaseListaGuionado
import mx.sigeh.app.testutil.RelojFijo
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramacionViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private data class Llamada(val desde: String, val hasta: String, val conNombres: Boolean, val fechaPadron: String)

    private class RepositorioGuionado : ProgramacionRepository {
        val llamadas = mutableListOf<Llamada>()
        var resultado: ResultadoProgramacion? = null

        override suspend fun semana(
            hospitalId: String,
            servicioId: String,
            desde: String,
            hasta: String,
            conNombres: Boolean,
            fechaPadron: String,
        ): ResultadoProgramacion {
            llamadas += Llamada(desde, hasta, conNombres, fechaPadron)
            resultado?.let { return it }
            val lunes = desde.take(10)
            return ResultadoProgramacion.Exito(
                (0 until 7).flatMap { DatosFicticios.programacionDelDia(servicioId, HoraHospital.sumarDias(lunes, it)) },
                nombresDisponibles = conNombres,
            )
        }
    }

    /** Viernes 20 nov 2026, 07:30 en la Ciudad de México. */
    private val reloj = RelojFijo(HoraHospital.instante("2026-11-20T13:30:00Z")!!)
    private val repo = RepositorioGuionado()
    private val sesion = SessionStoreEnMemoria(Session("u", "cuenta", "t"))

    private fun crear(servicio: mx.sigeh.app.core.auth.ServicioActivo = PaseListaGuionado.SERVICIO) =
        ProgramacionViewModel(ScopeCache().apply { seleccionarServicio(servicio) }, repo, PaseListaGuionado(), sesion, reloj)

    @Test
    fun semanaActual_deLunesALunesEnLaZonaDelHospital() = runTest {
        val vm = crear()

        assertEquals(
            Llamada("2026-11-16T00:00:00-06:00", "2026-11-23T00:00:00-06:00", conNombres = true, fechaPadron = "2026-11-16"),
            repo.llamadas.single(),
        )
        val estado = vm.uiState.value
        assertEquals("16–22 nov 2026", estado.semana)
        assertEquals(7, estado.dias.size)
        assertEquals("2026-11-20", estado.dias.single { it.seleccionado }.fecha)
        assertTrue(estado.dias.all { it.jornadas == 6 })
        assertEquals("Agrupado por turno, en orden de inicio", listOf("TM", "TV", "TN"), estado.grupos.map { it.turno })
        assertEquals(3, estado.grupos.first().filas.size)
        assertEquals("07:00–14:30", estado.grupos.first().filas.first().horario)
        assertEquals("21:00–07:00", estado.grupos.last().filas.single().horario)
    }

    @Test
    fun cambiarDeSemanaYDeDia() = runTest {
        val vm = crear()

        vm.onSemanaSiguiente()
        assertEquals("2026-11-23T00:00:00-06:00", repo.llamadas.last().desde)
        assertEquals("Conserva el día de la semana", "2026-11-27", vm.uiState.value.dias.single { it.seleccionado }.fecha)

        vm.onDiaSeleccionado("2026-11-25") // miércoles: hay una cancelada en el turno matutino
        assertTrue(vm.uiState.value.grupos.flatMap { it.filas }.any { it.estado == EstadoProgramacion.CANCELADA })
    }

    @Test
    fun sinPersonalConsultar_noSePidenNombres() = runTest {
        val servicio = PaseListaGuionado.SERVICIO.copy(
            facultades = PaseListaGuionado.SERVICIO.facultades.copy(consultarPersonal = false),
        )

        val vm = crear(servicio)

        assertFalse(repo.llamadas.single().conNombres)
        assertFalse(vm.uiState.value.nombresDisponibles)
    }

    @Test
    fun errores_bloqueanOOfrecenReintentar() = runTest {
        repo.resultado = ResultadoProgramacion.SinPermiso
        assertEquals(BloqueoProgramacion.SIN_PERMISO, crear().uiState.value.bloqueo)

        repo.resultado = ResultadoProgramacion.NoHabilitada
        assertEquals(BloqueoProgramacion.NO_HABILITADA, crear().uiState.value.bloqueo)

        repo.resultado = ResultadoProgramacion.Fallida
        val vm = crear()
        assertTrue(vm.uiState.value.error)
        repo.resultado = null
        vm.onReintentar()
        assertFalse(vm.uiState.value.error)

        repo.resultado = ResultadoProgramacion.SesionTerminada
        assertEquals(BloqueoProgramacion.SESION_TERMINADA, crear().uiState.value.bloqueo)
        assertTrue(sesion.actual()!!.vencida)
    }
}
