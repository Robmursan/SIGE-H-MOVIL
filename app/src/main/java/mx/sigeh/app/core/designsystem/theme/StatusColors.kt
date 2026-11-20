package mx.sigeh.app.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tonos de estado de SIGE-H. Ningún estado depende solo del color: todo componente que los usa
 * lleva también texto e ícono.
 */
enum class TonoEstado { POSITIVO, NEGATIVO, ADVERTENCIA, INFORMATIVO, NEUTRO }

/**
 * @param fondo fondo de chips, badges y avisos.
 * @param texto texto e íconos sobre [fondo] (contraste ≥ 7:1).
 * @param borde contorno de badges y de los botones de captura.
 * @param solido relleno del botón seleccionado o de «Registrar {estado}».
 * @param sobreSolido texto sobre [solido].
 * @param textoFuerte texto largo sobre [fondo] (avisos).
 */
@Immutable
data class ColoresTono(
    val fondo: Color,
    val texto: Color,
    val borde: Color,
    val solido: Color,
    val sobreSolido: Color,
    val textoFuerte: Color,
)

/**
 * Estados con la paleta semántica del sistema web (`--sigeh-status-*`), según la tabla del
 * prompt M2 §8. El relleno sólido usa el mismo tono que el texto, con blanco encima (≥ 7:1).
 */
@Immutable
data class SigehStatusColors(
    /** Presente, sincronizado, cubierta (`optimal`). */
    val positivo: ColoresTono,
    /** Ausente, conflicto (`critical`). */
    val negativo: ColoresTono,
    /** Parcial, en el teléfono, primera y única captura (`warning`). */
    val advertencia: ColoresTono,
    /** Por capturar, pendiente, informativo (`info`). */
    val informativo: ColoresTono,
    /** No capturable, cancelada, descanso, denegado. */
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
        private fun tono(fondo: Long, texto: Long, borde: Long) = ColoresTono(
            fondo = Color(fondo),
            texto = Color(texto),
            borde = Color(borde),
            solido = Color(texto),
            sobreSolido = Color.White,
            textoFuerte = Color(texto),
        )

        val Claro = SigehStatusColors(
            positivo = tono(fondo = 0xFFECFDF5, texto = 0xFF065F46, borde = 0xFF6EE7B7),
            negativo = tono(fondo = 0xFFFEF2F2, texto = 0xFF991B1B, borde = 0xFFFCA5A5),
            advertencia = tono(fondo = 0xFFFFFBEB, texto = 0xFF92400E, borde = 0xFFFCD34D),
            informativo = tono(fondo = 0xFFEFF6FF, texto = 0xFF1E40AF, borde = 0xFF93C5FD),
            neutro = tono(fondo = 0xFFF1F5F9, texto = 0xFF334155, borde = 0xFFCBD5E1),
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
