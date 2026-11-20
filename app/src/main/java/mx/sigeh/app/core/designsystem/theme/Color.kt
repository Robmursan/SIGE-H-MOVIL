package mx.sigeh.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta de SIGE-H alineada con el sistema web (SIGE-H-Fontend `src/styles/_tokens.scss`), para que
 * web y móvil se vean iguales (prompt M2 §8). Entre paréntesis, el token web equivalente.
 *
 * Única diferencia deliberada: el texto tenue es `#475569` (prompt M2) y no `#64748B`
 * (`--sigeh-text-muted`), para tener contraste 7:1 en pantallas usadas en pasillo.
 */
object SigehColors {
    /** Barra superior y marca (`--sigeh-primary-900`). */
    val Marca = Color(0xFF0F2942)

    /** Texto e íconos sobre [Marca] (`--sigeh-text-on-dark`). */
    val SobreMarca = Color(0xFFF8FAFC)

    /** Texto secundario sobre [Marca]. */
    val SobreMarcaTenue = Color(0xFFCBD5E1)

    /** Botones principales (`--sigeh-primary-800`). */
    val Primary = Color(0xFF163A5F)
    val OnPrimary = Color(0xFFFFFFFF)

    /** `--sigeh-primary-100`. */
    val PrimaryContainer = Color(0xFFDBEAFE)
    val OnPrimaryContainer = Color(0xFF0F2942)

    /** Acento clínico (`--sigeh-clinical-700`): progreso y confirmaciones del servidor. */
    val Acento = Color(0xFF0F766E)

    /** `--sigeh-clinical-50`. */
    val AcentoSuave = Color(0xFFF0FDFA)

    /** `--sigeh-clinical-800`. */
    val AcentoTexto = Color(0xFF115E59)

    /** `--sigeh-surface-bg`. */
    val Background = Color(0xFFF4F7FA)

    /** `--sigeh-surface-card`. */
    val Surface = Color(0xFFFFFFFF)

    /** Fondo neutro de chips y segmentos (estado neutro del prompt M2). */
    val SurfaceVariant = Color(0xFFF1F5F9)

    /** `--sigeh-text-primary`. */
    val OnSurface = Color(0xFF0F172A)

    /** Texto tenue (ver nota de la clase). */
    val OnSurfaceVariant = Color(0xFF475569)

    /** `--sigeh-border-strong`. */
    val Outline = Color(0xFFCBD5E1)

    /** `--sigeh-border-subtle`. */
    val OutlineVariant = Color(0xFFE2E8F0)

    /** Banner oscuro «Sin conexión» y superficies invertidas. */
    val InverseSurface = Color(0xFF0F2942)

    /** `--sigeh-status-critical-text`. */
    val Error = Color(0xFF991B1B)

    /** Fondo de una opción seleccionada (`--sigeh-primary-50`). */
    val SeleccionSuave = Color(0xFFEFF6FF)

    /** Títulos de sección y texto de apoyo oscuro (`--sigeh-text-secondary`). */
    val Tinta = Color(0xFF334155)

    /** Marcador de posición de los campos (`--sigeh-text-muted`). */
    val Marcador = Color(0xFF64748B)

    // Tarjeta oscura de sincronización.
    val SuperficieOscuraElevada = Color(0xFF163A5F)
    val TextoSobreOscuro = Color(0xFFCBD5E1)

    // Tarjeta principal de la agenda (sobre Primary).
    val HeroEtiqueta = Color(0xFFDBEAFE)
    val HeroTexto = Color(0xFFEFF6FF)
    val HeroChip = Color(0xFF1E4E79)
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
    tertiary = SigehColors.Acento,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = SigehColors.AcentoSuave,
    onTertiaryContainer = SigehColors.AcentoTexto,
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
    inverseOnSurface = SigehColors.SobreMarca,
    inversePrimary = SigehColors.PrimaryContainer,
    outline = SigehColors.Outline,
    outlineVariant = SigehColors.OutlineVariant,
    error = SigehColors.Error,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = SigehColors.Error,
    scrim = Color(0xFF000000),
)
