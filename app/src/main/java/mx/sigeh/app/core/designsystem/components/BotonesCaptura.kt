package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.model.EstadoCaptura

/**
 * Presente, Ausente y Parcial para una jornada `capturable` (reemplaza a `AttendanceSelector` en la
 * lista). Nada queda marcado: cada botón solo **abre la confirmación** con ese estado.
 *
 * Con [enabled] en false (sin red o con un envío en curso) los tres se desactivan juntos, así un
 * doble toque nunca produce dos intenciones.
 *
 * @param nombre persona de la fila, para que TalkBack diga «Registrar Presente de Ana Rosales».
 */
@Composable
fun BotonesCaptura(
    onPresente: () -> Unit,
    onAusente: () -> Unit,
    onParcial: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    nombre: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonEstado(EstadoCaptura.PRESENTE, onPresente, enabled, nombre, Modifier.weight(1f))
        BotonEstado(EstadoCaptura.AUSENTE, onAusente, enabled, nombre, Modifier.weight(1f))
        BotonEstado(EstadoCaptura.PARCIAL, onParcial, enabled, nombre, Modifier.weight(1f))
    }
}

@Composable
private fun BotonEstado(
    estado: EstadoCaptura,
    onClick: () -> Unit,
    enabled: Boolean,
    nombre: String?,
    modifier: Modifier,
) {
    val apariencia = estado.apariencia()
    val colores = SigehThemeExtras.estados.para(apariencia.tono)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = SigehShapes.Campo,
        border = BorderStroke(1.5.dp, if (enabled) colores.borde else SigehColors.OutlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = colores.texto,
            disabledContainerColor = SigehColors.SurfaceVariant,
            disabledContentColor = SigehColors.OnSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("captura_${estado.name}")
            .semantics {
                contentDescription = if (nombre != null) "Registrar ${apariencia.texto} de $nombre" else "Registrar ${apariencia.texto}"
            },
    ) {
        Icon(apariencia.icono, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(apariencia.texto, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun BotonesCapturaPreview() {
    SigehTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonesCaptura(onPresente = {}, onAusente = {}, onParcial = {}, enabled = true, nombre = "Jorge Salas")
            BotonesCaptura(onPresente = {}, onAusente = {}, onParcial = {}, enabled = false)
        }
    }
}
