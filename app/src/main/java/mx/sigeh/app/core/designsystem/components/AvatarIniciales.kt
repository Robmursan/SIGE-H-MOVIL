package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme

/**
 * Iniciales de un nombre visible (`nombres + " " + primerApellido`): primera letra del primer y del
 * último término. «Ana Sofía Rosales» → «AR»; «Héctor Ruiz» → «HR»; «Ana» → «A».
 */
fun iniciales(nombre: String): String {
    val partes = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (partes.size) {
        0 -> ""
        1 -> partes[0].take(1)
        else -> partes.first().take(1) + partes.last().take(1)
    }.uppercase(Locale.ROOT)
}

/** Avatar circular con iniciales. Es decorativo: el nombre siempre va escrito al lado. */
@Composable
fun AvatarIniciales(
    nombre: String,
    modifier: Modifier = Modifier,
    tamano: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(tamano)
            .background(SigehColors.PrimaryContainer, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = iniciales(nombre),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = SigehColors.OnPrimaryContainer,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AvatarInicialesPreview() {
    SigehTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarIniciales("Ana Rosales")
            AvatarIniciales("Héctor Ruiz")
            AvatarIniciales("Lucía Navarro", tamano = 56.dp)
        }
    }
}
