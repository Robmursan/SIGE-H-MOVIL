package mx.sigeh.app.core.data.fake

import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.data.ResultadoProgramacion
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.testutil.SessionStoreEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProgramacionRepositoryTest {

    private val sesion = SessionStoreEnMemoria(
        Session(DatosFicticios.usuario.id, DatosFicticios.usuario.nombreCorto, "m0-ficticio", asignaciones = DatosFicticios.asignaciones),
    )
    private val repositorio = FakeProgramacionRepository(sesion)
    private val ventana = HoraHospital.ventanaSemana("2026-11-16", HoraHospital.ZONA_POR_DEFECTO)

    private suspend fun semana(servicioId: String, conNombres: Boolean) = repositorio.semana(
        DatosFicticios.hospitalA.id, servicioId, ventana.first, ventana.second, conNombres, "2026-11-16",
    )

    @Test
    fun semana_sieteDiasConTresTurnosYCambios() = runTest {
        val resultado = semana(DatosFicticios.medicinaInterna.id, conNombres = true) as ResultadoProgramacion.Exito

        assertTrue(resultado.nombresDisponibles)
        val dias = resultado.jornadas.map { it.inicioProgramado.take(10) }.toSet()
        assertEquals(7, dias.size)
        assertEquals("2026-11-16", dias.min())
        assertEquals(setOf("TM", "TV", "TN"), resultado.jornadas.mapNotNull { it.turnoCodigo }.toSet())
        assertTrue(resultado.jornadas.any { it.estado == EstadoProgramacion.CANCELADA })
        assertTrue(resultado.jornadas.any { it.estado == EstadoProgramacion.SUSTITUIDA })
    }

    @Test
    fun sinPersonalConsultar_noHayNombres() = runTest {
        // Cirugía General solo tiene PROGRAMACION_CONSULTAR en las asignaciones Fake.
        val resultado = semana(DatosFicticios.cirugiaGeneral.id, conNombres = true) as ResultadoProgramacion.Exito

        assertFalse(resultado.nombresDisponibles)
        assertTrue(resultado.jornadas.all { it.nombre == null && it.nombreVisible.startsWith("Trabajador ··· ") })
    }

    @Test
    fun servicioSinPermisoDeProgramacion_esSinPermiso() = runTest {
        assertEquals(ResultadoProgramacion.SinPermiso, semana("svc-ajeno", conNombres = false))
    }
}
