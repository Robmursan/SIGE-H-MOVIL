package mx.sigeh.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import mx.sigeh.app.R

/**
 * Inter (400, 500, 600 y 700) con el proveedor de Google Fonts de Compose. No se empaqueta
 * ningún binario de fuente: si Google Play Services no está disponible, Compose usa la fuente
 * del sistema como respaldo.
 */
private val proveedorGoogleFonts = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.sigeh_google_fonts_certs,
)

private val inter = GoogleFont("Inter")

val InterFamily: FontFamily = FontFamily(
    Font(googleFont = inter, fontProvider = proveedorGoogleFonts, weight = FontWeight.Normal),
    Font(googleFont = inter, fontProvider = proveedorGoogleFonts, weight = FontWeight.Medium),
    Font(googleFont = inter, fontProvider = proveedorGoogleFonts, weight = FontWeight.SemiBold),
    Font(googleFont = inter, fontProvider = proveedorGoogleFonts, weight = FontWeight.Bold),
)

private fun estilo(tamano: Int, peso: FontWeight, interlineado: Int, espaciado: Float = 0f) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = peso,
    fontSize = tamano.sp,
    lineHeight = interlineado.sp,
    letterSpacing = espaciado.sp,
)

/**
 * Escala tipográfica (prompt §4):
 * - titleLarge: títulos de barra, 22 sp en 700.
 * - titleMedium: nombres en listas, 16 sp en 600.
 * - bodySmall: texto secundario, 13 sp.
 * - labelMedium: etiquetas, 12 sp en 600.
 */
val SigehTypography = Typography(
    displaySmall = estilo(32, FontWeight.Bold, 38, -0.5f),
    headlineMedium = estilo(26, FontWeight.Bold, 32),
    headlineSmall = estilo(22, FontWeight.Bold, 28),
    titleLarge = estilo(22, FontWeight.Bold, 28),
    titleMedium = estilo(16, FontWeight.SemiBold, 22),
    titleSmall = estilo(14, FontWeight.SemiBold, 20),
    bodyLarge = estilo(16, FontWeight.Normal, 22),
    bodyMedium = estilo(15, FontWeight.Normal, 22),
    bodySmall = estilo(13, FontWeight.Normal, 19),
    labelLarge = estilo(15, FontWeight.SemiBold, 20),
    labelMedium = estilo(12, FontWeight.SemiBold, 16),
    labelSmall = estilo(11, FontWeight.Medium, 14),
)

/** Cifras con dígitos tabulares para que no "bailen" al cambiar. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
