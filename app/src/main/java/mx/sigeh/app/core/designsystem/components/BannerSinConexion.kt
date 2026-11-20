package mx.sigeh.app.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.sync.ConnectivityObserver

const val TEXTO_SIN_CONEXION_CAPTURA = "Sin conexión. Los botones de captura se desactivan hasta reconectar."

/**
 * Banner oscuro de M2 (prompt §9.6): la captura es solo en línea, así que sin red no se guarda nada
 * en el teléfono. Aparece únicamente cuando [ConnectivityObserver] informa que no hay red.
 */
@Composable
fun BannerSinConexion(
    conectividad: ConnectivityObserver,
    modifier: Modifier = Modifier,
) {
    val enLinea by conectividad.enLinea.collectAsStateWithLifecycle()
    BannerSinConexion(visible = !enLinea, modifier = modifier)
}

/** Versión sin estado, para vistas previas y pruebas. */
@Composable
fun BannerSinConexion(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SigehColors.InverseSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("banner_sin_conexion"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SigehIcons.WifiOff, contentDescription = null, tint = SigehColors.SobreMarca, modifier = Modifier.size(18.dp))
            Text(
                TEXTO_SIN_CONEXION_CAPTURA,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = SigehColors.SobreMarca,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun BannerSinConexionPreview() {
    SigehTheme {
        Column {
            BannerSinConexion(visible = true)
        }
    }
}
