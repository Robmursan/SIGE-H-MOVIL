package mx.sigeh.app.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tonos de estado de SIGE-H. Ningún estado depende solo del color: todo componente que los usa
 * lleva también texto o ícono.
 */
enum class TonoEstado { POSITIVO, NEGATIVO, ADVERTENCIA, INFORMATIVO, NEUTRO }

/**
 * @param fondo fondo de chips y avisos.
 * @param texto texto sobre [fondo].
 * @param solido relleno del botón seleccionado.
 * @param sobreSolido texto sobre [solido].
 * @param textoFuerte texto largo sobre [fondo] (avisos), algo más oscuro para mejor contraste.
 */
@Immutable
data class ColoresTono(
    val fondo: Color,
    val texto: Color,
    val solido: Color,
    val sobreSolido: Color,
    val textoFuerte: Color,
)

@Immutable
data class SigehStatusColors(
    /** Presente, sincronizado, cubierta. */
    val positivo: ColoresTono,
    /** Ausente, conflicto. */
    val negativo: ColoresTono,
    /** Parcial, en el teléfono, en asignación. */
    val advertencia: ColoresTono,
    /** Informativo, festivo, convenio. */
    val informativo: ColoresTono,
    /** Neutro, descanso, denegado. */
    val neutro: ColoresTono,
) {
    fun para(tono: TonoEstado): ColoresTono = when (tono) {
        TonoEstado.POSITIVO -> positivo
        TonoEstado.NEGATIVO -> negativo
        TonoEstado.ADVERTENCIA -> advertencia
        TonoEstado.INFORMATIVO -> informativo
        TonoEstado.NEUTRO -> neutro
    }

    companion object {
        val Claro = SigehStatusColors(
            positivo = ColoresTono(
                fondo = Color(0xFFD4ECEB), texto = Color(0xFF08393D),
                solido = Color(0xFF0E5C63), sobreSolido = Color.White, textoFuerte = Color(0xFF08393D),
            ),
            negativo = ColoresTono(
                fondo = Color(0xFFFBE4DA), texto = Color(0xFF8A2B0C),
                solido = Color(0xFFA3340F), sobreSolido = Color.White, textoFuerte = Color(0xFF5E1E08),
            ),
            advertencia = ColoresTono(
                fondo = Color(0xFFFCEFD9), texto = Color(0xFF7A4A00),
                solido = Color(0xFF7A4A00), sobreSolido = Color.White, textoFuerte = Color(0xFF5C3800),
            ),
            informativo = ColoresTono(
                fondo = Color(0xFFE0E8FB), texto = Color(0xFF1E3A8A),
                solido = Color(0xFF1E3A8A), sobreSolido = Color.White, textoFuerte = Color(0xFF1E3A8A),
            ),
            neutro = ColoresTono(
                fondo = Color(0xFFE8EFEF), texto = Color(0xFF24363A),
                solido = Color(0xFF24363A), sobreSolido = Color.White, textoFuerte = Color(0xFF24363A),
            ),
        )
    }
}

val LocalSigehStatusColors = staticCompositionLocalOf { SigehStatusColors.Claro }

object SigehThemeExtras {
    val estados: SigehStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSigehStatusColors.current
}
