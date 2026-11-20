package mx.sigeh.app.core.auth

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.RolAlcance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoliticaAmbitoTest {

    private val titularFake = DatosFicticios.alcances(0L).first { it.rol == RolAlcance.TITULAR }
    private val encargadoFake = DatosFicticios.alcances(0L).first { it.rol == RolAlcance.ENCARGADO }

    private fun asignacion(rol: RolAlcance?) = AsignacionSesion(rol = rol, hospitalId = "h")

    @Test
    fun modoFake_usaElRolDelCatalogo() {
        assertEquals(RolAlcance.TITULAR, PoliticaAmbito.rolEfectivo(titularFake, asignaciones = null))
        assertEquals(RolAlcance.ENCARGADO, PoliticaAmbito.rolEfectivo(encargadoFake, asignaciones = null))
    }

    @Test
    fun sinAsignacionesReales_seDeniegaTodo() {
        assertNull(PoliticaAmbito.rolEfectivo(titularFake, emptyList()))
        assertNull(PoliticaAmbito.rolEfectivo(titularFake, listOf(asignacion(null))))
    }

    @Test
    fun soloEncargosReales_nuncaSeOtorgaTitular() {
        assertEquals(RolAlcance.ENCARGADO, PoliticaAmbito.rolEfectivo(titularFake, listOf(asignacion(RolAlcance.ENCARGADO))))
    }

    @Test
    fun titularReal_conservaTitularSoloDondeElCatalogoLoEs() {
        val reales = listOf(asignacion(RolAlcance.TITULAR))
        assertEquals(RolAlcance.TITULAR, PoliticaAmbito.rolEfectivo(titularFake, reales))
        assertEquals(RolAlcance.ENCARGADO, PoliticaAmbito.rolEfectivo(encargadoFake, reales))
    }
}
