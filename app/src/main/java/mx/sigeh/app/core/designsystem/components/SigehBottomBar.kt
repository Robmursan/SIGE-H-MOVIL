package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme

enum class SigehTab(val etiqueta: String, val icono: ImageVector) {
    PASE("Pase", SigehIcons.ClipboardCheck),
    INCIDENCIAS("Incidencias", SigehIcons.AlertTriangle),
    MOVIMIENTOS("Movimientos", SigehIcons.Repeat),
    AGENDA("Agenda", SigehIcons.Calendar),
}

/** Barra inferior: Pase, Incidencias, Movimientos y Agenda. Indicador activo en primaryContainer. */
@Composable
fun SigehBottomBar(
    actual: SigehTab,
    onSeleccionar: (SigehTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            SigehTab.entries.forEach { tab ->
                val seleccionado = tab == actual
                NavigationBarItem(
                    selected = seleccionado,
                    onClick = { onSeleccionar(tab) },
                    icon = { Icon(tab.icono, contentDescription = null) },
                    label = {
                        Text(
                            tab.etiqueta,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SigehColors.OnPrimaryContainer,
                        selectedTextColor = SigehColors.OnPrimaryContainer,
                        indicatorColor = SigehColors.PrimaryContainer,
                        unselectedIconColor = SigehColors.OnSurfaceVariant,
                        unselectedTextColor = SigehColors.OnSurfaceVariant,
                    ),
                    modifier = Modifier.testTag("tab_${tab.name}"),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SigehBottomBarPreview() {
    SigehTheme { SigehBottomBar(actual = SigehTab.PASE, onSeleccionar = {}) }
}
