package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes

/** Botón principal en píldora (52 dp por defecto, 48 dp en barras de acciones dobles). */
@Composable
fun SigehPrimaryButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    alto: Dp = 52.dp,
    icono: ImageVector? = null,
    color: Color = SigehColors.Primary,
) {
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        shape = SigehShapes.Pildora,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = SigehColors.OutlineVariant,
            disabledContentColor = SigehColors.OnSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(alto),
    ) {
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = SigehColors.OnSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
        } else if (icono != null) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, style = MaterialTheme.typography.labelLarge.copy(fontSize = if (alto >= 52.dp) 16.sp else 15.sp))
    }
}

/** Botón secundario con contorno de 1.5 dp (primario o de error para "Rechazar"). */
@Composable
fun SigehOutlinedButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    alto: Dp = 48.dp,
    color: Color = SigehColors.Primary,
    forma: androidx.compose.ui.graphics.Shape = SigehShapes.Pildora,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        shape = forma,
        border = BorderStroke(1.5.dp, if (habilitado) color else SigehColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = color,
            disabledContainerColor = Color.White,
            disabledContentColor = SigehColors.OnSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
        modifier = modifier.height(alto),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}
