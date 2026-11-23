package mx.sigeh.app.core.auth

import mx.sigeh.app.core.model.RolAlcance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PoliticaAmbito.ambitosDeContextos]: misma regla que con `/auth/me`, ahora con nombres, zona y vigencia. */
class PoliticaContextosTest {

    private fun contexto(
        asignacion: String,
        servicioId: String? = SERVICIO,
        divisionId: String? = DIVISION,
        rol: RolAlcance? = RolAlcance.TITULAR,
        permisos: List<String> = listOf(CodigosPermiso.ASISTENCIA_CAPTURAR),
        vigenteHasta: String? = null,
        servicioNombre: String? = "Medicina Interna",
        zona: String = "America/Mexico_City",
    ) = ContextoPropio(
        assignmentId = asignacion,
        hospitalId = HOSPITAL,
        hospitalNombre = "Hospital A",
        zonaHoraria = zona,
        divisionId = divisionId,
        divisionNombre = divisionId?.let { "División de Medicina" },
        servicioId = servicioId,
        servicioNombre = servicioId?.let { servicioNombre },
        rol = rol,
        vigenteDesde = "2026-11-01T06:00:00.000Z",
        vigenteHasta = vigenteHasta,
        permisos = permisos,
    )

    @Test
    fun contextoSinPermisoDeAsistencia_noSeOfrece() {
        val ambitos = PoliticaAmbito.ambitosDeContextos(
            listOf(contexto("a1", servicioId = null, divisionId = null, permisos = listOf(CodigosPermiso.CATALOGOS_ADMINISTRAR))),
        )
        assertTrue("El administrador web no ve ámbitos de asistencia", ambitos.isEmpty())
    }

    @Test
    fun sinContextos_noSeOfreceNada() {
        assertTrue(PoliticaAmbito.ambitosDeContextos(emptyList()).isEmpty())
    }

    @Test
    fun mismoAmbito_seUneConPermisosSumadosYTitularGana() {
        val ambito = PoliticaAmbito.ambitosDeContextos(
            listOf(
                contexto("a1", rol = RolAlcance.ENCARGADO, permisos = listOf(CodigosPermiso.ASISTENCIA_VALIDAR), vigenteHasta = "2026-12-01T06:00:00.000Z"),
                contexto("a2", rol = RolAlcance.TITULAR, permisos = listOf(CodigosPermiso.ASISTENCIA_CAPTURAR)),
            ),
        ).single()

        assertEquals(RolAlcance.TITULAR, ambito.rol)
        assertTrue(ambito.facultades.capturar)
        assertTrue(ambito.facultades.validar)
        assertNull("Una asignación sin término hace que el ámbito no tenga término", ambito.vigenteHasta)
        assertEquals("Hospital A", ambito.hospitalNombre)
        assertEquals("División de Medicina", ambito.divisionNombre)
        assertEquals("Medicina Interna", ambito.servicioNombre)
        assertEquals("America/Mexico_City", ambito.zonaHoraria)
    }

    @Test
    fun dosEncargos_seConservaLaVigenciaMasLejana() {
        val ambito = PoliticaAmbito.ambitosDeContextos(
            listOf(
                contexto("a1", rol = RolAlcance.ENCARGADO, vigenteHasta = "2026-12-15T06:00:00.000Z"),
                // Mismo instante expresado con offset: se compara por instante, no por texto.
                contexto("a2", rol = RolAlcance.ENCARGADO, vigenteHasta = "2026-12-01T00:00:00.000-06:00"),
            ),
        ).single()

        assertEquals(RolAlcance.ENCARGADO, ambito.rol)
        assertEquals("2026-12-15T06:00:00.000Z", ambito.vigenteHasta)
    }

    @Test
    fun vigenciaIlegible_noSeMuestra() {
        val ambito = PoliticaAmbito.ambitosDeContextos(
            listOf(contexto("a1", rol = RolAlcance.ENCARGADO, vigenteHasta = "pronto")),
        ).single()
        assertNull(ambito.vigenteHasta)
    }

    @Test
    fun sinServicio_vaAlFinalYDeshabilitado_sinInventarDescendientes() {
        val ambitos = PoliticaAmbito.ambitosDeContextos(
            listOf(
                contexto("a1", servicioId = null, permisos = listOf(CodigosPermiso.ASISTENCIA_VALIDAR)),
                contexto("a2"),
            ),
        )

        assertEquals(listOf(SERVICIO, null), ambitos.map { it.servicioId })
        assertTrue(ambitos[0].habilitado)
        assertFalse(ambitos[1].habilitado)
        assertNull(ambitos[1].servicioNombre)
    }

    @Test
    fun nombreVacio_quedaNuloParaUsarLaEtiquetaHonesta() {
        val ambito = PoliticaAmbito.ambitosDeContextos(listOf(contexto("a1", servicioNombre = "  "))).single()
        assertNull(ambito.servicioNombre)
    }

    @Test
    fun rolDesconocido_noOtorgaNiQuitaFacultades() {
        val ambito = PoliticaAmbito.ambitosDeContextos(listOf(contexto("a1", rol = null))).single()
        assertNull(ambito.rol)
        assertTrue(ambito.facultades.capturar)
    }

    private companion object {
        const val HOSPITAL = "6a1f4e2c-0000-4000-8000-0000000000b1"
        const val DIVISION = "6a1f4e2c-0000-4000-8000-0000000000c1"
        const val SERVICIO = "6a1f4e2c-0000-4000-8000-0000000000d1"
    }
}
