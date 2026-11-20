package mx.sigeh.app.core.designsystem.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme

/**
 * Barra superior con el color de marca del sistema web (#0F2942): título (22 sp, 700), subtítulo
 * opcional, flecha de regreso opcional y acciones (normalmente el [SyncPendingChip]).
 * [contenidoInferior] (contexto del pase o filtros) va debajo, sobre blanco.
 *
 * Mientras haya una barra de marca en pantalla, los íconos de la barra de estado se dibujan claros.
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
    IconosClarosEnBarraDeEstado()
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(color = SigehColors.Marca, contentColor = SigehColors.SobreMarca, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = if (onRegresar != null) 8.dp else 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onRegresar != null) {
                    IconButton(onClick = onRegresar) {
                        Icon(SigehIcons.ArrowLeft, contentDescription = descripcionRegresar, tint = SigehColors.SobreMarca)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = titulo,
                        style = MaterialTheme.typography.titleLarge,
                        color = SigehColors.SobreMarca,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitulo != null) {
                        Text(text = subtitulo, style = MaterialTheme.typography.bodySmall, color = SigehColors.SobreMarcaTenue)
                    }
                }
                CompositionLocalProvider(LocalContentColor provides SigehColors.SobreMarca) {
                    acciones()
                }
            }
        }
        if (contenidoInferior != null) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    content = contenidoInferior,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * Cuenta cuántas barras de marca hay en composición. Con un contador (y no con «restaurar el valor
 * anterior») el resultado no depende del orden en que la navegación compone y descarta pantallas:
 * mientras quede una barra, íconos claros; al no quedar ninguna (login, selector), oscuros.
 */
private object BarrasDeMarca {
    var activas = 0
}

@Composable
private fun IconosClarosEnBarraDeEstado() {
    val vista = LocalView.current
    if (vista.isInEditMode) return
    DisposableEffect(vista) {
        val ventana = vista.context.buscarActividad()?.window
        val controlador = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        BarrasDeMarca.activas++
        controlador?.isAppearanceLightStatusBars = false
        onDispose {
            BarrasDeMarca.activas--
            // La app solo tiene tema claro: sin barra de marca, la barra de estado va sobre fondo claro.
            if (BarrasDeMarca.activas == 0) controlador?.isAppearanceLightStatusBars = true
        }
    }
}

private tailrec fun Context.buscarActividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.buscarActividad()
    else -> null
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SigehTopBarPreview() {
    SigehTheme {
        Column {
            SigehTopBar(
                titulo = "Resumen del pase",
                subtitulo = "Medicina Interna · Matutino · Sáb 14 nov",
                onRegresar = {},
                acciones = { SyncPendingChip(porEnviar = 3, onClick = {}) },
            )
            SigehTopBar(
                titulo = "Medicina Interna",
                subtitulo = "Hospital A · America/Mexico_City",
                contenidoInferior = {
                    Text("Viernes 20 nov · Todos los turnos", style = MaterialTheme.typography.labelLarge)
                },
            )
        }
    }
}
