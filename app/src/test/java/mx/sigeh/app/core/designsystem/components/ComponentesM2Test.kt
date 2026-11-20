package mx.sigeh.app.core.designsystem.components

import androidx.compose.material3.SnackbarDuration
import mx.sigeh.app.core.designsystem.theme.SigehStatusColors
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica sin interfaz de los componentes de M2 (las pruebas de pantalla quedan para M2-F5). */
class ComponentesM2Test {

    @Test
    fun iniciales_primerYUltimoTermino() {
        assertEquals("AR", iniciales("Ana Sofía Rosales"))
        assertEquals("HR", iniciales("Héctor Ruiz"))
        assertEquals("LN", iniciales("  lucía   navarro "))
        assertEquals("A", iniciales("Ana"))
        assertEquals("", iniciales("   "))
    }

    @Test
    fun progreso_siempreDiceEnEstaPagina() {
        assertEquals("3 de 5 capturables registradas · en esta página", textoProgresoPagina(3, 5))
    }

    @Test
    fun cuentaRegresiva_minutosYSegundos() {
        assertEquals("1:00", formatoCuentaRegresiva(60))
        assertEquals("0:42", formatoCuentaRegresiva(42))
        assertEquals("2:05", formatoCuentaRegresiva(125))
        assertEquals("0:00", formatoCuentaRegresiva(-3))
    }

    @Test
    fun snackbarAcuse_textoDelPromptYAccionVerAcuse() {
        val visuales = AcuseSnackbarVisuals(nombre = "Jorge Salas", estado = EstadoCaptura.AUSENTE, hora = "07:05:12")

        assertEquals("Registrado en el servidor · Jorge Salas · Ausente · 07:05:12", visuales.message)
        assertEquals("Ver acuse", visuales.actionLabel)
        assertEquals(SnackbarDuration.Long, visuales.duration)
    }

    @Test
    fun estadosOperativos_cadaUnoConTextoTonoEIconoPropios() {
        val capturables = EstadoCaptura.entries.map { it.apariencia() }

        assertEquals(listOf("Presente", "Ausente", "Parcial"), capturables.map { it.texto })
        assertEquals(listOf(TonoEstado.POSITIVO, TonoEstado.NEGATIVO, TonoEstado.ADVERTENCIA), capturables.map { it.tono })
        assertEquals("No se distinguen solo por color: íconos distintos", 3, capturables.map { it.icono }.distinct().size)
        EstadoAsistencia.entries.forEach { assertTrue(it.apariencia().texto.isNotBlank()) }
    }

    @Test
    fun pendiente_esPorCapturarSoloSiElServidorLoPermite() {
        val porCapturar = EstadoAsistencia.PENDIENTE.apariencia(capturable = true)
        val noCapturable = EstadoAsistencia.PENDIENTE.apariencia(capturable = false)

        assertEquals("Por capturar", porCapturar.texto)
        assertEquals(TonoEstado.INFORMATIVO, porCapturar.tono)
        assertEquals(TonoEstado.NEUTRO, noCapturable.tono)
        assertNotEquals(porCapturar.texto, noCapturable.texto)
    }

    @Test
    fun programacion_soloProgramadaNoEsNeutra() {
        assertEquals(TonoEstado.INFORMATIVO, EstadoProgramacion.PROGRAMADA.apariencia().tono)
        EstadoProgramacion.entries.filter { it != EstadoProgramacion.PROGRAMADA }.forEach {
            assertEquals(TonoEstado.NEUTRO, it.apariencia().tono)
        }
    }

    @Test
    fun paleta_coincideConLaTablaDelPrompt() {
        val estados = SigehStatusColors.Claro
        // Presente, Ausente, Parcial, Pendiente/informativo y Neutro: fondo, texto y borde (prompt M2 §8).
        val esperado = mapOf(
            TonoEstado.POSITIVO to Triple(0xFFECFDF5, 0xFF065F46, 0xFF6EE7B7),
            TonoEstado.NEGATIVO to Triple(0xFFFEF2F2, 0xFF991B1B, 0xFFFCA5A5),
            TonoEstado.ADVERTENCIA to Triple(0xFFFFFBEB, 0xFF92400E, 0xFFFCD34D),
            TonoEstado.INFORMATIVO to Triple(0xFFEFF6FF, 0xFF1E40AF, 0xFF93C5FD),
            TonoEstado.NEUTRO to Triple(0xFFF1F5F9, 0xFF334155, 0xFFCBD5E1),
        )
        esperado.forEach { (tono, valores) ->
            val colores = estados.para(tono)
            assertEquals(tono.name, androidx.compose.ui.graphics.Color(valores.first), colores.fondo)
            assertEquals(tono.name, androidx.compose.ui.graphics.Color(valores.second), colores.texto)
            assertEquals(tono.name, androidx.compose.ui.graphics.Color(valores.third), colores.borde)
            assertFalse("El relleno sólido no puede ser el fondo suave", colores.solido == colores.fondo)
        }
    }
}
