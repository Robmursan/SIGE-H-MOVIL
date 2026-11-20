package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.tabular
import mx.sigeh.app.core.model.EstadoAsistencia

/**
 * Tres botones de 48 dp: Presente, Ausente y Parcial. El seleccionado va relleno (y con check, para
 * no depender solo del color); los demás, con contorno. Semántica de grupo de opciones `selected`.
 */
@Composable
fun AttendanceSelector(
    seleccionado: EstadoAsistencia?,
    onSeleccionar: (EstadoAsistencia) -> Unit,
    modifier: Modifier = Modifier,
    nombre: String = "",
    habilitado: Boolean = true,
) {
    val estados = SigehThemeExtras.estados
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Asistencia de $nombre" }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OPCIONES.forEach { (estado, etiqueta) ->
            val esSeleccionado = seleccionado == estado
            val relleno = when (estado) {
                EstadoAsistencia.PRESENTE -> estados.positivo.solido
                EstadoAsistencia.AUSENTE -> estados.negativo.solido
                else -> estados.advertencia.solido
            }
            Surface(
                selected = esSeleccionado,
                onClick = { onSeleccionar(estado) },
                enabled = habilitado,
                shape = SigehShapes.Campo,
                color = if (esSeleccionado) relleno else Color.White,
                contentColor = if (esSeleccionado) Color.White else SigehColors.Tinta,
                border = BorderStroke(1.5.dp, if (esSeleccionado) relleno else SigehColors.Outline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { role = Role.RadioButton }
                    .testTag("asistencia_${estado.name}"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (esSeleccionado) {
                            Icon(SigehIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(etiqueta, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

private val OPCIONES = listOf(
    EstadoAsistencia.PRESENTE to "Presente",
    EstadoAsistencia.AUSENTE to "Ausente",
    EstadoAsistencia.PARCIAL to "Parcial",
)

/** Programados, Exigibles, Capturados, Pendientes y Validados (calculados en vivo). */
@Composable
fun CounterStrip(
    programados: Int,
    exigibles: Int,
    capturados: Int,
    pendientes: Int,
    validados: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Contador(programados, "Programados", Modifier.weight(1f))
        Contador(exigibles, "Exigibles", Modifier.weight(1f))
        Contador(capturados, "Capturados", Modifier.weight(1f))
        Contador(pendientes, "Pendientes", Modifier.weight(1f), color = SigehThemeExtras.estados.advertencia.texto)
        Contador(validados, "Validados", Modifier.weight(1f))
    }
}

@Composable
private fun Contador(valor: Int, etiqueta: String, modifier: Modifier, color: Color = SigehColors.OnSurface) {
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag("contador_$etiqueta"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = valor.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold).tabular(),
            color = color,
        )
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = SigehColors.OnSurfaceVariant, maxLines = 1)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun AttendanceSelectorPreview() {
    SigehTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AttendanceSelector(seleccionado = EstadoAsistencia.PRESENTE, onSeleccionar = {}, nombre = "Ana Rosales M.")
            AttendanceSelector(seleccionado = null, onSeleccionar = {}, nombre = "Lucía Navarro P.")
            CounterStrip(programados = 8, exigibles = 7, capturados = 4, pendientes = 3, validados = 0)
        }
    }
}
