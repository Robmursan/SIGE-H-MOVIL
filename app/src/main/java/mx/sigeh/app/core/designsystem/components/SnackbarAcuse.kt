package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.model.EstadoCaptura

/**
 * Datos del snackbar que confirma una captura **después del 200** (prompt M2 §9.5).
 * Se muestra con `SnackbarHostState.showSnackbar(AcuseSnackbarVisuals(...))`; si el resultado es
 * `ActionPerformed`, quien llama abre el acuse («Ver acuse»).
 *
 * @param hora `HH:mm:ss` del `registradoEn` del servidor, en la zona del hospital.
 */
class AcuseSnackbarVisuals(
    val nombre: String,
    val estado: EstadoCaptura,
    val hora: String,
) : SnackbarVisuals {
    override val message: String = "Registrado en el servidor · $nombre · ${estado.apariencia().texto} · $hora"
    override val actionLabel: String = "Ver acuse"
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/**
 * Snackbar del acuse, con ícono de confirmación y la acción «Ver acuse». En el `SnackbarHost`:
 * `{ datos -> if (datos.visuals is AcuseSnackbarVisuals) SnackbarAcuse(datos) else Snackbar(datos) }`.
 */
@Composable
fun SnackbarAcuse(datos: SnackbarData, modifier: Modifier = Modifier) {
    ContenidoSnackbarAcuse(
        mensaje = datos.visuals.message,
        accion = datos.visuals.actionLabel,
        onAccion = datos::performAction,
        modifier = modifier,
    )
}

@Composable
private fun ContenidoSnackbarAcuse(
    mensaje: String,
    accion: String?,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier
            .padding(12.dp)
            .testTag("snackbar_acuse"),
        containerColor = SigehColors.Marca,
        contentColor = SigehColors.SobreMarca,
        action = accion?.let {
            {
                TextButton(onClick = onAccion, modifier = Modifier.testTag("snackbar_ver_acuse")) {
                    Text(it, color = SigehColors.PrimaryContainer, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(SigehIcons.CloudCheck, contentDescription = null, tint = SigehColors.AcentoSuave, modifier = Modifier.size(20.dp))
            Text(mensaje, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun SnackbarAcusePreview() {
    SigehTheme {
        val visuales = AcuseSnackbarVisuals(nombre = "Jorge Salas", estado = EstadoCaptura.PRESENTE, hora = "07:05:12")
        ContenidoSnackbarAcuse(mensaje = visuales.message, accion = visuales.actionLabel, onAccion = {})
    }
}
