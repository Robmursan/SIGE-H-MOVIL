package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** "N por enviar" en ámbar con reloj. Abre la pantalla de Sincronización. */
@Composable
fun SyncPendingChip(
    porEnviar: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hayPendientes = porEnviar > 0
    val tono = SigehThemeExtras.estados.para(if (hayPendientes) TonoEstado.ADVERTENCIA else TonoEstado.POSITIVO)
    Surface(
        onClick = onClick,
        shape = SigehShapes.Pildora,
        color = tono.fondo,
        contentColor = tono.texto,
        modifier = modifier.testTag("sync_chip"),
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (hayPendientes) SigehIcons.Clock else SigehIcons.CloudCheck,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (hayPendientes) "$porEnviar por enviar" else "Todo enviado",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Banner de conexión. Sin red: fondo #24363A y wifi tachado. En línea: fondo #D4ECEB y nube con check.
 */
@Composable
fun ConnectionBanner(
    enLinea: Boolean,
    modifier: Modifier = Modifier,
    textoSinConexion: String = TEXTO_SIN_CONEXION_PASE,
    textoEnLinea: String = TEXTO_EN_LINEA_PASE,
) {
    val fondo = if (enLinea) SigehColors.PrimaryContainer else SigehColors.InverseSurface
    val texto = if (enLinea) SigehColors.OnPrimaryContainer else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fondo)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            // Se fusiona el texto para que TalkBack anuncie el cambio de conexión completo.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(if (enLinea) "banner_en_linea" else "banner_sin_conexion"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (enLinea) SigehIcons.CloudCheck else SigehIcons.WifiOff,
            contentDescription = null,
            tint = texto,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Text(
            text = if (enLinea) textoEnLinea else textoSinConexion,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = texto,
        )
    }
}

const val TEXTO_SIN_CONEXION_PASE =
    "Sin conexión. Lo que captures se guarda en este teléfono y se envía al volver la red. No es validación final."
const val TEXTO_EN_LINEA_PASE = "En línea. Cada captura se envía al servidor al momento."

/** Píldora de estado: siempre con texto (y opcionalmente ícono), nunca solo color. */
@Composable
fun StatusPill(
    texto: String,
    tono: TonoEstado,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
) {
    val colores = SigehThemeExtras.estados.para(tono)
    Row(
        modifier = modifier
            .background(colores.fondo, SigehShapes.Chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icono != null) {
            Icon(icono, contentDescription = null, tint = colores.texto, modifier = Modifier.size(14.dp))
        }
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = colores.texto,
            maxLines = 1,
        )
    }
}

/** Aviso con ícono: neutro (#E8EFEF) o ámbar (#FCEFD9); también admite los demás tonos. */
@Composable
fun InfoCallout(
    texto: String,
    modifier: Modifier = Modifier,
    tono: TonoEstado = TonoEstado.NEUTRO,
    icono: ImageVector = if (tono == TonoEstado.ADVERTENCIA || tono == TonoEstado.NEGATIVO) {
        SigehIcons.AlertTriangle
    } else {
        SigehIcons.Info
    },
    titulo: String? = null,
) {
    val colores = SigehThemeExtras.estados.para(tono)
    val colorIcono = if (tono == TonoEstado.NEUTRO) SigehColors.Primary else colores.texto
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colores.fondo, SigehShapes.Aviso)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = colorIcono,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (titulo != null) {
                Text(titulo, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colores.textoFuerte)
            }
            Text(texto, style = MaterialTheme.typography.bodySmall, color = colores.textoFuerte)
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun IndicadoresPreview() {
    SigehTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SyncPendingChip(porEnviar = 3, onClick = {})
            ConnectionBanner(enLinea = false)
            ConnectionBanner(enLinea = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Aplicado", TonoEstado.POSITIVO)
                StatusPill("Conflicto", TonoEstado.NEGATIVO)
                StatusPill("En el teléfono", TonoEstado.ADVERTENCIA)
            }
            InfoCallout("Copia guardada en el teléfono a las 06:52.")
            InfoCallout("Registro preliminar. Supervisión o Recursos Humanos lo clasifican.", tono = TonoEstado.ADVERTENCIA)
        }
    }
}
