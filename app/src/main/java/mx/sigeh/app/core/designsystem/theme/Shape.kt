package mx.sigeh.app.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas (prompt §4): chips 8 dp; campos y botones segmentados 12 dp; tarjetas 16 dp;
 * tarjetas destacadas 18–20 dp; botones principales en píldora.
 */
object SigehShapes {
    val Chip = RoundedCornerShape(8.dp)
    val Campo = RoundedCornerShape(12.dp)
    val Aviso = RoundedCornerShape(14.dp)
    val Tarjeta = RoundedCornerShape(16.dp)
    val TarjetaDestacada = RoundedCornerShape(18.dp)
    val TarjetaPrincipal = RoundedCornerShape(20.dp)
    val Pildora = CircleShape
}

val SigehMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
