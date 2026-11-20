package mx.sigeh.app.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Tema de SIGE-H. M0 solo tiene esquema claro y el color dinámico está desactivado a propósito.
 *
 * Lugar preparado para el modo oscuro: cuando exista `SigehDarkColorScheme` (y su
 * `SigehStatusColors.Oscuro`), elegirlo aquí con `isSystemInDarkTheme()`.
 */
@Composable
fun SigehTheme(content: @Composable () -> Unit) {
    val colorScheme = SigehLightColorScheme
    val estados = SigehStatusColors.Claro
    CompositionLocalProvider(LocalSigehStatusColors provides estados) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SigehTypography,
            shapes = SigehMaterialShapes,
            content = content,
        )
    }
}
