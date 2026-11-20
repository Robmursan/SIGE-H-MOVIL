package mx.sigeh.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Paleta exacta de SIGE-H (prompt §4) más los tonos complementarios usados en los mockups. */
object SigehColors {
    val Primary = Color(0xFF0E5C63)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFD4ECEB)
    val OnPrimaryContainer = Color(0xFF08393D)
    val Background = Color(0xFFF4F7F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFE8EFEF)
    val OnSurface = Color(0xFF132124)
    val OnSurfaceVariant = Color(0xFF4B5B5F)
    val Outline = Color(0xFFA9B8BB)
    val OutlineVariant = Color(0xFFDCE4E5)
    val InverseSurface = Color(0xFF24363A)
    val Error = Color(0xFFA3340F)

    /** Fondo de una opción seleccionada (servicio, turno, tipo de incidencia). */
    val SeleccionSuave = Color(0xFFEEF7F6)

    /** Títulos de sección y texto de apoyo oscuro. */
    val Tinta = Color(0xFF24363A)

    /** Marcador de posición de los campos. */
    val Marcador = Color(0xFF6B7A7E)

    // Tarjeta oscura de sincronización.
    val SuperficieOscuraElevada = Color(0xFF31474C)
    val TextoSobreOscuro = Color(0xFFD5DFE1)

    // Tarjeta principal de la agenda.
    val HeroEtiqueta = Color(0xFFCDE8E6)
    val HeroTexto = Color(0xFFE3F2F1)
    val HeroChip = Color(0xFF2A7A80)
}

/** Esquema claro. El color dinámico está desactivado para respetar la identidad institucional. */
val SigehLightColorScheme: ColorScheme = lightColorScheme(
    primary = SigehColors.Primary,
    onPrimary = SigehColors.OnPrimary,
    primaryContainer = SigehColors.PrimaryContainer,
    onPrimaryContainer = SigehColors.OnPrimaryContainer,
    secondary = SigehColors.Primary,
    onSecondary = SigehColors.OnPrimary,
    secondaryContainer = SigehColors.PrimaryContainer,
    onSecondaryContainer = SigehColors.OnPrimaryContainer,
    tertiary = Color(0xFF1E3A8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E8FB),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = SigehColors.Background,
    onBackground = SigehColors.OnSurface,
    surface = SigehColors.Surface,
    onSurface = SigehColors.OnSurface,
    surfaceVariant = SigehColors.SurfaceVariant,
    onSurfaceVariant = SigehColors.OnSurfaceVariant,
    surfaceTint = SigehColors.Primary,
    surfaceBright = SigehColors.Surface,
    surfaceDim = SigehColors.SurfaceVariant,
    surfaceContainerLowest = SigehColors.Surface,
    surfaceContainerLow = SigehColors.Surface,
    surfaceContainer = SigehColors.Surface,
    surfaceContainerHigh = SigehColors.Surface,
    surfaceContainerHighest = SigehColors.SurfaceVariant,
    inverseSurface = SigehColors.InverseSurface,
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = SigehColors.PrimaryContainer,
    outline = SigehColors.Outline,
    outlineVariant = SigehColors.OutlineVariant,
    error = SigehColors.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE4DA),
    onErrorContainer = Color(0xFF8A2B0C),
    scrim = Color(0xFF000000),
)
