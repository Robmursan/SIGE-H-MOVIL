package mx.sigeh.app.features.paselista

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.model.EstadoAsistencia
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaseListaScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun mostrar(estadoInicial: PaseListaUiState, alSeleccionar: (String, EstadoAsistencia) -> Unit = { _, _ -> }) {
        compose.setContent {
            SigehTheme {
                PaseListaScreen(
                    estado = estadoInicial,
                    onRegresar = {},
                    onAbrirSincronizacion = {},
                    onEstadoSeleccionado = alSeleccionar,
                    onAbrirConflicto = {},
                    onGuardarBorrador = {},
                    onEnviarAValidar = {},
                    onMensajeMostrado = {},
                )
            }
        }
    }

    @Test
    fun muestraLasOchoFilasDelPase() {
        val estado = paseListaEjemplo(enLinea = false)
        assertEquals(8, estado.filas.size)
        mostrar(estado)

        DatosFicticios.jornadas.forEach { jornada ->
            compose.onNodeWithTag("lista_pase").performScrollToNode(hasTestTag("fila_${jornada.programacionId}"))
            compose.onNodeWithTag("fila_${jornada.programacionId}").assertIsDisplayed()
        }
        compose.onNodeWithTag("contador_Programados").assert(hasText("8"))
        compose.onNodeWithTag("contador_Exigibles").assert(hasText("7"))
    }

    @Test
    fun elSelectorCambiaElEstadoDeLaFila() {
        var estado by mutableStateOf(paseListaEjemplo(enLinea = false))
        compose.setContent {
            SigehTheme {
                PaseListaScreen(
                    estado = estado,
                    onRegresar = {},
                    onAbrirSincronizacion = {},
                    onEstadoSeleccionado = { id, nuevo ->
                        estado = estado.copy(
                            filas = estado.filas.map {
                                if (it.programacionId == id) it.copy(estado = nuevo, indicador = IndicadorFila.EN_TELEFONO) else it
                            },
                        )
                    },
                    onAbrirConflicto = {},
                    onGuardarBorrador = {},
                    onEnviarAValidar = {},
                    onMensajeMostrado = {},
                )
            }
        }
        val fila = hasAnyAncestor(hasTestTag("fila_prog-0003"))
        compose.onNodeWithTag("lista_pase").performScrollToNode(hasTestTag("fila_prog-0003"))
        val presente = compose.onNode(hasTestTag("asistencia_PRESENTE") and fila)

        presente.assertIsNotSelected()
        presente.performClick()

        presente.assertIsSelected()
        compose.onNode(hasTestTag("asistencia_AUSENTE") and fila).assertIsNotSelected()
        compose.onNode(hasText("En el teléfono") and fila).assertIsDisplayed()
    }

    @Test
    fun elBannerSinConexionApareceCuandoNoHayRed() {
        var estado by mutableStateOf(paseListaEjemplo(enLinea = false))
        compose.setContent {
            SigehTheme {
                PaseListaScreen(
                    estado = estado,
                    onRegresar = {},
                    onAbrirSincronizacion = {},
                    onEstadoSeleccionado = { _, _ -> },
                    onAbrirConflicto = {},
                    onGuardarBorrador = {},
                    onEnviarAValidar = {},
                    onMensajeMostrado = {},
                )
            }
        }

        compose.onNodeWithTag("banner_sin_conexion").assertIsDisplayed()
        compose.onNodeWithTag("banner_sin_conexion").assert(hasText("Sin conexión", substring = true))

        estado = estado.copy(enLinea = true)
        compose.onNodeWithTag("banner_en_linea").assertIsDisplayed()
        compose.onNodeWithTag("banner_sin_conexion").assertDoesNotExist()
    }
}
