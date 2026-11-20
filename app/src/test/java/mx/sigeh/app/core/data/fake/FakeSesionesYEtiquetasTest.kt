package mx.sigeh.app.core.data.fake

import kotlinx.coroutines.test.runTest
import mx.sigeh.app.core.data.EtiquetasPorIdentificador
import mx.sigeh.app.core.data.ResultadoRevocacion
import mx.sigeh.app.core.data.ResultadoSesiones
import mx.sigeh.app.testutil.RelojFijo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSesionesYEtiquetasTest {

    @Test
    fun sesionesFake_tienenActualActivaYRevocada() = runTest {
        val sesiones = (FakeSesionesRepository(RelojFijo(0L)).sesiones() as ResultadoSesiones.Exito).sesiones

        assertEquals(DatosFicticios.SESION_ACTUAL_ID, sesiones.single { it.actual }.sessionId)
        assertEquals(1, sesiones.count { it.revocada })
        assertEquals(1, sesiones.count { !it.actual && !it.revocada })
    }

    @Test
    fun revocarFake_marcaLaSesionYAvisaSiEraLaActual() = runTest {
        val repositorio = FakeSesionesRepository(RelojFijo(1_763_643_900_000L))

        assertEquals(ResultadoRevocacion.Revocada(eraLaActual = false), repositorio.revocar("ses-0002"))
        assertEquals(ResultadoRevocacion.Revocada(eraLaActual = true), repositorio.revocar(DatosFicticios.SESION_ACTUAL_ID))
        assertEquals(ResultadoRevocacion.NoEncontrada, repositorio.revocar("ses-9999"))

        val sesiones = (repositorio.sesiones() as ResultadoSesiones.Exito).sesiones
        assertTrue(sesiones.single { it.sessionId == "ses-0002" }.revocada)
        assertTrue(sesiones.single { it.sessionId == "ses-0002" }.revocadaEn!!.endsWith("Z"))
    }

    @Test
    fun etiquetasReales_muestranElFinalDelIdentificadorSinInventarNombres() = runTest {
        val etiquetas = EtiquetasPorIdentificador()
        val id = "6A1F4E2C-0000-4000-8000-00000000C91E"

        assertEquals("Servicio ··· c91e", etiquetas.etiquetaServicio(id))
        assertEquals("Hospital ··· c91e", etiquetas.etiquetaHospital(id))
        assertEquals("División ··· c91e", etiquetas.etiquetaDivision(id))
    }

    @Test
    fun etiquetasFake_usanLosNombresFicticiosYSiNoLosConocenElTextoReal() = runTest {
        val etiquetas = FakeEtiquetasContexto()

        assertEquals("Medicina Interna", etiquetas.etiquetaServicio(DatosFicticios.medicinaInterna.id))
        assertEquals("División de Cirugía", etiquetas.etiquetaDivision(DatosFicticios.cirugiaGeneral.divisionId))
        assertEquals("Hospital A", etiquetas.etiquetaHospital(DatosFicticios.hospitalA.id))
        assertEquals("Servicio ··· abcd", etiquetas.etiquetaServicio("uuid-real-abcd"))
    }
}
