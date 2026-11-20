package mx.sigeh.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoraHospitalTest {

    private val mx = HoraHospital.ZONA_POR_DEFECTO

    /** 2026-11-20 13:04:12.345 UTC. */
    private val referencia = 1_795_179_852_345L

    @Test
    fun instante_aceptaZOffsetsYFracciones() {
        assertEquals(referencia, HoraHospital.instante("2026-11-20T13:04:12.345Z"))
        assertEquals(referencia, HoraHospital.instante("2026-11-20T07:04:12.345-06:00"))
        assertEquals("Microsegundos de PostgreSQL", referencia, HoraHospital.instante("2026-11-20T07:04:12.345678-06:00"))
        assertEquals(referencia - 345, HoraHospital.instante("2026-11-20T07:04:12-0600"))
        assertEquals(referencia - 12_345, HoraHospital.instante("2026-11-20T13:04Z"))
        assertNull(HoraHospital.instante("2026-11-20T07:04:12"))
        assertNull(HoraHospital.instante("ayer"))
    }

    @Test
    fun horas_enLaZonaDelHospitalYNoEnLaDelTelefono() {
        assertEquals("07:04", HoraHospital.hora("2026-11-20T13:04:12.345Z", mx))
        assertEquals("07:04:12", HoraHospital.horaConSegundos("2026-11-20T13:04:12.345Z", mx))
        assertEquals("13:04", HoraHospital.hora("2026-11-20T07:04:12-06:00", "UTC"))
        assertEquals("07:00–14:30", HoraHospital.intervalo("2026-11-20T07:00:00.000-06:00", "2026-11-20T14:30:00.000-06:00", mx))
        assertEquals("--:--", HoraHospital.hora("sin hora", mx))
    }

    @Test
    fun hoy_dependeDeLaZona() {
        // 2026-11-21 03:00 UTC es todavía el día 20 en la Ciudad de México.
        val madrugadaUtc = HoraHospital.instante("2026-11-21T03:00:00Z")!!
        assertEquals("2026-11-20", HoraHospital.hoy(madrugadaUtc, mx))
        assertEquals("2026-11-21", HoraHospital.hoy(madrugadaUtc, "UTC"))
    }

    @Test
    fun zonaDesconocida_usaLaDePorDefecto() {
        assertEquals(mx, HoraHospital.zona("Marte/Olympus").id)
        assertEquals(mx, HoraHospital.zona(null).id)
        assertEquals("America/Tijuana", HoraHospital.zona("America/Tijuana").id)
    }

    @Test
    fun aritmeticaDeDiasYSemanas() {
        assertEquals("2026-11-16", HoraHospital.lunesDe("2026-11-20"))
        assertEquals("2026-11-16", HoraHospital.lunesDe("2026-11-16"))
        assertEquals("2026-11-16", HoraHospital.lunesDe("2026-11-22"))
        assertEquals("2026-12-01", HoraHospital.sumarDias("2026-11-30", 1))
        assertEquals(6, HoraHospital.diasEntre("2026-11-16", "2026-11-22"))
        assertEquals("16–22 nov 2026", HoraHospital.rangoSemana("2026-11-16"))
        assertEquals("30 nov – 6 dic 2026", HoraHospital.rangoSemana("2026-11-30"))
        assertEquals("Vie 20 nov", HoraHospital.fechaCorta("2026-11-20"))
        assertEquals("Viernes 20 nov 2026", HoraHospital.fechaLarga("2026-11-20"))
        assertEquals("Lun 16", HoraHospital.diaCorto("2026-11-16"))
    }

    @Test
    fun ventanaSemana_deLunesALunesConOffsetYNoMasDe168Horas() {
        val (desde, hasta) = HoraHospital.ventanaSemana("2026-11-16", mx)

        assertEquals("2026-11-16T00:00:00-06:00", desde)
        assertEquals("2026-11-23T00:00:00-06:00", hasta)
        assertEquals(HoraHospital.VENTANA_MAXIMA_MS, HoraHospital.instante(hasta)!! - HoraHospital.instante(desde)!!)
    }

    @Test
    fun ventanaSemana_conCambioDeHorarioSeRecortaA168Horas() {
        // Semana del cambio de horario de otoño en Estados Unidos (1 nov 2026): 169 h reales.
        val (desde, hasta) = HoraHospital.ventanaSemana("2026-10-26", "America/New_York")

        assertEquals(HoraHospital.VENTANA_MAXIMA_MS, HoraHospital.instante(hasta)!! - HoraHospital.instante(desde)!!)
    }

    @Test
    fun fechaHora_deSesiones() {
        assertEquals("20 nov 07:00", HoraHospital.fechaHora("2026-11-20T13:00:00.000Z", mx))
    }
}
