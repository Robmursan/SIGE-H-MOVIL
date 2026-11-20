package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme

/**
 * Barra superior: título (22 sp, 700), subtítulo opcional, flecha de regreso opcional y acciones
 * (normalmente el [SyncPendingChip]). [contenidoInferior] aloja el contexto del pase o filtros.
 */
@Composable
fun SigehTopBar(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    onRegresar: (() -> Unit)? = null,
    descripcionRegresar: String = "Regresar",
    acciones: @Composable RowScope.() -> Unit = {},
    contenidoInferior: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = if (onRegresar != null) 8.dp else 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onRegresar != null) {
                    IconButton(onClick = onRegresar) {
                        Icon(SigehIcons.ArrowLeft, contentDescription = descripcionRegresar, tint = SigehColors.Tinta)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitulo != null) {
                        Text(
                            text = subtitulo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                acciones()
            }
            if (contenidoInferior != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, bottom = 12.dp),
                    content = contenidoInferior,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SigehTopBarPreview() {
    SigehTheme {
        SigehTopBar(
            titulo = "Resumen del pase",
            subtitulo = "Medicina Interna · Matutino · Sáb 14 nov",
            onRegresar = {},
            acciones = { SyncPendingChip(porEnviar = 3, onClick = {}) },
        )
    }
}
