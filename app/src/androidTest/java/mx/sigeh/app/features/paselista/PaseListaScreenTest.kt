package mx.sigeh.app.features.paselista

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoProgramacion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pantalla del pase en línea (prompt M2 §11, pruebas instrumentadas). Datos ficticios. */
@RunWith(AndroidJUnit4::class)
class PaseListaScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun estadoDePrueba(enLinea: Boolean = true) = PaseListaUiState(
        cargando = false,
        servicio = "Medicina Interna",
        subtitulo = "Hospital A · America/Mexico_City",
        fecha = "2026-11-20",
        fechaTexto = "Viernes 20 nov 2026",
        filas = listOf(
            FilaJornada(
                "p-01", "Ana Rosales", "TM · 07:00–14:30", TipoFila.CAPTURADA, EstadoAsistencia.PRESENTE,
                EstadoProgramacion.PROGRAMADA, "Capturado por Tú · 07:04 · validación pendiente", null, resaltada = false,
            ),
            FilaJornada(
                "p-02", "Jorge Salas", "TM · 07:00–14:30", TipoFila.CAPTURABLE, EstadoAsistencia.PENDIENTE,
                EstadoProgramacion.PROGRAMADA, null, null, resaltada = false,
            ),
            FilaJornada(
                "p-06", "Daniela Cruz", "TM · 07:00–14:30", TipoFila.NO_CAPTURABLE, EstadoAsistencia.NO_EXIGIBLE,
                EstadoProgramacion.CANCELADA, null, "No capturable: programación cancelada", resaltada = false,
            ),
        ),
        capturadas = 1,
        capturables = 2,
        conteos = mapOf(EstadoAsistencia.PRESENTE to 1, EstadoAsistencia.PENDIENTE to 1),
        enLinea = enLinea,
    )

    @Test
    fun losTresTiposDeFila() {
        compose.setContent {
            SigehTheme { PaseListaScreen(estadoDePrueba(), AccionesPaseLista(), SnackbarHostState()) }
        }

        compose.onAllNodesWithTag("captura_PRESENTE").assertCountEquals(1)
        compose.onNodeWithText("Capturado por Tú · 07:04 · validación pendiente").assertIsDisplayed()
        compose.onNodeWithText("No capturable: programación cancelada").assertIsDisplayed()
        compose.onNodeWithTag("badge_programacion_CANCELADA").assertIsDisplayed()
        compose.onNodeWithText("1 de 2 capturables registradas · en esta página").assertIsDisplayed()
    }

    @Test
    fun laConfirmacionAbreConElEstadoElegidoYCancelarNoEnviaNada() {
        var estado by mutableStateOf(estadoDePrueba())
        var confirmaciones = 0
        compose.setContent {
            SigehTheme {
                PaseListaScreen(
                    estado = estado,
                    acciones = AccionesPaseLista(
                        onAbrirCaptura = { id, elegido ->
                            estado = estado.copy(hoja = HojaCaptura(id, "Jorge Salas", "TM · 07:00–14:30 · Medicina Interna", elegido, enviando = false))
                        },
                        onConfirmar = { confirmaciones++ },
                        onCancelarHoja = { estado = estado.copy(hoja = null) },
                    ),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithTag("captura_AUSENTE").performClick()
        compose.onNodeWithTag("confirmar_captura").assertIsDisplayed()
        compose.onNodeWithTag("confirmar_estado_AUSENTE").assertIsSelected()
        compose.onNode(hasText("Registrar Ausente")).assertIsDisplayed()
        compose.onNodeWithTag("confirmar_cancelar").performClick()
        compose.waitForIdle()

        assertEquals(null, estado.hoja)
        assertEquals("Cancelar no envía nada", 0, confirmaciones)
    }

    @Test
    fun sinConexion_bannerYBotonesDesactivados() {
        compose.setContent {
            SigehTheme { PaseListaScreen(estadoDePrueba(enLinea = false), AccionesPaseLista(), SnackbarHostState()) }
        }

        compose.onNodeWithTag("banner_sin_conexion").assertIsDisplayed()
        compose.onNodeWithTag("captura_PRESENTE").assertIsNotEnabled()
        compose.onNodeWithTag("captura_AUSENTE").assertIsNotEnabled()
        compose.onNode(hasTestTag("pase_actualizar")).assertIsEnabled()
    }
}
