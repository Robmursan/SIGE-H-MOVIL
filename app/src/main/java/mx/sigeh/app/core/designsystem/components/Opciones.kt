package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme

/**
 * Tarjeta seleccionable con radio, para tipos de incidencia y opciones de conflicto.
 * El radio va al final (incidencias) o al inicio (conflicto).
 */
@Composable
fun OptionCard(
    titulo: String,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detalle: String? = null,
    radioAlInicio: Boolean = false,
    habilitado: Boolean = true,
) {
    Surface(
        selected = seleccionado,
        onClick = onClick,
        enabled = habilitado,
        shape = if (radioAlInicio) SigehShapes.Tarjeta else RoundedCornerShape(14.dp),
        color = if (seleccionado) SigehColors.SeleccionSuave else Color.White,
        border = BorderStroke(2.dp, if (seleccionado) SigehColors.Primary else SigehColors.OutlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics { role = Role.RadioButton },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (radioAlInicio) 14.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (radioAlInicio) Alignment.Top else Alignment.CenterVertically,
        ) {
            if (radioAlInicio) RadioIndicador(seleccionado, Modifier.padding(top = 1.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (radioAlInicio) 4.dp else 2.dp)) {
                Text(titulo, style = MaterialTheme.typography.labelLarge, color = SigehColors.OnSurface)
                if (detalle != null) {
                    Text(
                        detalle,
                        style = if (radioAlInicio) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                        color = SigehColors.OnSurfaceVariant,
                    )
                }
            }
            if (!radioAlInicio) RadioIndicador(seleccionado)
        }
    }
}

@Composable
private fun RadioIndicador(seleccionado: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .border(2.dp, if (seleccionado) SigehColors.Primary else SigehColors.Outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (seleccionado) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(SigehColors.Primary),
            )
        }
    }
}

/** Segmentos en contenedor (#E8EFEF): el activo en blanco. Para filtros tipo pestaña. */
@Composable
fun SigehSegmentedTabs(
    opciones: List<String>,
    seleccionado: Int,
    onSeleccionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SigehColors.SurfaceVariant, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opciones.forEachIndexed { indice, texto ->
            val activo = indice == seleccionado
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activo) Color.White else Color.Transparent)
                    .selectable(selected = activo, role = Role.Tab, onClick = { onSeleccionar(indice) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    texto,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (activo) SigehColors.OnPrimaryContainer else SigehColors.OnSurfaceVariant,
                )
            }
        }
    }
}

/** Botones segmentados con contorno (12 dp): el elegido va relleno en el color primario. */
@Composable
fun SigehSegmentedButtons(
    opciones: List<String>,
    seleccionado: Int,
    onSeleccionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opciones.forEachIndexed { indice, texto ->
            val activo = indice == seleccionado
            Surface(
                selected = activo,
                onClick = { onSeleccionar(indice) },
                enabled = habilitado,
                shape = SigehShapes.Campo,
                color = if (activo) SigehColors.Primary else Color.White,
                contentColor = if (activo) Color.White else SigehColors.Tinta,
                border = BorderStroke(1.5.dp, if (activo) SigehColors.Primary else SigehColors.Outline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { role = Role.RadioButton },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(texto, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun OpcionesPreview() {
    SigehTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OptionCard("Retardo", seleccionado = true, onClick = {}, detalle = "Ausencia parcial")
            OptionCard("Licencia con goce", seleccionado = false, onClick = {}, detalle = "Licencias")
            OptionCard(
                "Conservar lo del servidor",
                seleccionado = true,
                onClick = {},
                detalle = "Tu captura local se descarta y queda constancia del intento.",
                radioAlInicio = true,
            )
            SigehSegmentedTabs(listOf("Del servicio", "Para mí · 1"), seleccionado = 0, onSeleccionar = {})
            SigehSegmentedButtons(listOf("Jornada completa", "Parte de la jornada"), seleccionado = 1, onSeleccionar = {})
        }
    }
}
