package mx.sigeh.app.core.auth

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.RolAlcance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- M2: ámbitos y facultades desde los códigos de permiso de /auth/me ----------------------

    private fun asignacion(
        rol: RolAlcance?,
        servicioId: String?,
        vararg permisos: String,
        divisionId: String? = "div-1",
    ) = AsignacionSesion(rol = rol, hospitalId = "hosp-1", divisionId = divisionId, servicioId = servicioId, permisos = permisos.toList())

    @Test
    fun ambitos_soloEntranLosQueTienenPermisoDeAsistencia() {
        val ambitos = PoliticaAmbito.ambitosAutorizados(
            listOf(
                asignacion(RolAlcance.TITULAR, "svc-1", CodigosPermiso.ASISTENCIA_CAPTURAR),
                asignacion(RolAlcance.TITULAR, "svc-2", CodigosPermiso.ASISTENCIA_VALIDAR),
                asignacion(RolAlcance.TITULAR, "svc-3", CodigosPermiso.PROGRAMACION_CONSULTAR, CodigosPermiso.PERSONAL_CONSULTAR),
                asignacion(RolAlcance.TITULAR, "svc-4", CodigosPermiso.CATALOGOS_ADMINISTRAR),
                asignacion(RolAlcance.TITULAR, "svc-5"),
            ),
        )

        assertEquals(listOf("svc-1", "svc-2"), ambitos.map { it.servicioId })
    }

    @Test
    fun ambitoSinServicio_seOfreceDeshabilitadoYAlFinal() {
        val ambitos = PoliticaAmbito.ambitosAutorizados(
            listOf(
                asignacion(RolAlcance.TITULAR, servicioId = null, CodigosPermiso.ASISTENCIA_CAPTURAR),
                asignacion(RolAlcance.ENCARGADO, "svc-1", CodigosPermiso.ASISTENCIA_VALIDAR),
            ),
        )

        assertEquals(listOf("svc-1", null), ambitos.map { it.servicioId })
        assertTrue(ambitos[0].habilitado)
        assertFalse("Sin servicio no se enumeran servicios: queda deshabilitado", ambitos[1].habilitado)
        assertEquals("div-1", ambitos[1].divisionId)
    }

    @Test
    fun facultades_salenDelCodigoYNoDelNombreDelRol() {
        val (encargadoQueCaptura, titularQueSoloConsulta) = PoliticaAmbito.ambitosAutorizados(
            listOf(
                asignacion(RolAlcance.ENCARGADO, "svc-1", CodigosPermiso.ASISTENCIA_CAPTURAR),
                asignacion(RolAlcance.TITULAR, "svc-2", CodigosPermiso.ASISTENCIA_VALIDAR),
            ),
        )

        assertTrue(encargadoQueCaptura.facultades.capturar)
        assertFalse(encargadoQueCaptura.facultades.validar)
        assertTrue(encargadoQueCaptura.facultades.consultarPase)
        assertFalse(titularQueSoloConsulta.facultades.capturar)
        assertTrue(titularQueSoloConsulta.facultades.validar)
        assertTrue(titularQueSoloConsulta.facultades.consultarPase)
        assertFalse(titularQueSoloConsulta.facultades.consultarProgramacion)
    }

    @Test
    fun asignacionesDelMismoAmbito_seUnen() {
        val ambitos = PoliticaAmbito.ambitosAutorizados(
            listOf(
                asignacion(RolAlcance.ENCARGADO, "svc-1", CodigosPermiso.ASISTENCIA_VALIDAR),
                asignacion(RolAlcance.TITULAR, "svc-1", CodigosPermiso.ASISTENCIA_CAPTURAR, CodigosPermiso.PROGRAMACION_CONSULTAR),
                asignacion(rol = null, "svc-1", CodigosPermiso.PERSONAL_CONSULTAR),
            ),
        )

        val unico = ambitos.single()
        assertEquals(RolAlcance.TITULAR, unico.rol)
        assertEquals(
            setOf(
                CodigosPermiso.ASISTENCIA_VALIDAR,
                CodigosPermiso.ASISTENCIA_CAPTURAR,
                CodigosPermiso.PROGRAMACION_CONSULTAR,
                CodigosPermiso.PERSONAL_CONSULTAR,
            ),
            unico.permisos,
        )
    }

    @Test
    fun permisosSinAsistencia_noBastanParaOfrecerUnAmbito() {
        val ambitos = PoliticaAmbito.ambitosAutorizados(
            listOf(
                asignacion(RolAlcance.TITULAR, "svc-1", CodigosPermiso.PERSONAL_CONSULTAR),
                asignacion(RolAlcance.TITULAR, "svc-1", CodigosPermiso.PROGRAMACION_CONSULTAR),
            ),
        )

        assertTrue(ambitos.isEmpty())
    }

    @Test
    fun kindNulo_seAceptaSiTienePermisos() {
        val ambito = PoliticaAmbito.ambitosAutorizados(listOf(asignacion(rol = null, "svc-1", CodigosPermiso.ASISTENCIA_CAPTURAR))).single()

        assertNull(ambito.rol)
        assertTrue(ambito.facultades.capturar)
    }

    @Test
    fun sinAsignaciones_noSeOfreceNada() {
        assertTrue(PoliticaAmbito.ambitosAutorizados(emptyList()).isEmpty())
    }

    @Test
    fun facultadesEn_unServicio_soloConAsignacionesDeEseServicio() {
        val asignaciones = listOf(
            asignacion(RolAlcance.TITULAR, "svc-1", CodigosPermiso.ASISTENCIA_CAPTURAR),
            // Una asignación de división no cubre servicios: la app no sabe cuáles le pertenecen.
            asignacion(RolAlcance.TITULAR, servicioId = null, CodigosPermiso.ASISTENCIA_CAPTURAR, CodigosPermiso.PROGRAMACION_CONSULTAR),
        )

        assertTrue(PoliticaAmbito.facultadesEn("svc-1", asignaciones).capturar)
        assertFalse(PoliticaAmbito.facultadesEn("svc-1", asignaciones).consultarProgramacion)
        assertFalse(PoliticaAmbito.facultadesEn("svc-2", asignaciones).consultarPase)
    }

    @Test
    fun asignacionesFake_tienenLosTresCasosDelModoDemostracion() {
        val ambitos = PoliticaAmbito.ambitosAutorizados(DatosFicticios.asignaciones)

        assertEquals(3, ambitos.size)
        assertEquals(2, ambitos.count { it.habilitado })
        assertTrue(ambitos.single { it.servicioId == DatosFicticios.medicinaInterna.id }.facultades.capturar)
        assertFalse(ambitos.single { it.servicioId == DatosFicticios.cirugiaGeneral.id }.facultades.capturar)
        assertTrue("En Fake siempre hay Programación", ambitos.filter { it.habilitado }.all { it.facultades.consultarProgramacion })
    }
}
