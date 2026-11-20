package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion

/**
 * Cómo se ve un estado: texto, tono e ícono. Es el único lugar que lo define, para que lista,
 * hoja de confirmación, acuse y detalle coincidan. Nunca se distingue un estado solo por color.
 */
data class AparienciaEstado(
    val texto: String,
    val tono: TonoEstado,
    val icono: ImageVector,
)

/**
 * Estado operativo de una jornada.
 * @param capturable solo importa con `PENDIENTE`: «Por capturar» si el servidor permite capturar,
 *   «Sin capturar» (neutro) si no.
 */
fun EstadoAsistencia.apariencia(capturable: Boolean = false): AparienciaEstado = when (this) {
    EstadoAsistencia.PRESENTE -> AparienciaEstado("Presente", TonoEstado.POSITIVO, SigehIcons.UserCheck)
    EstadoAsistencia.AUSENTE -> AparienciaEstado("Ausente", TonoEstado.NEGATIVO, SigehIcons.UserX)
    EstadoAsistencia.PARCIAL -> AparienciaEstado("Parcial", TonoEstado.ADVERTENCIA, SigehIcons.Clock)
    EstadoAsistencia.PENDIENTE ->
        if (capturable) {
            AparienciaEstado("Por capturar", TonoEstado.INFORMATIVO, SigehIcons.CircleDashed)
        } else {
            AparienciaEstado("Sin capturar", TonoEstado.NEUTRO, SigehIcons.CircleDashed)
        }
    EstadoAsistencia.NO_EXIGIBLE -> AparienciaEstado("No exigible", TonoEstado.NEUTRO, SigehIcons.MinusCircle)
}

fun EstadoCaptura.apariencia(): AparienciaEstado = aEstadoAsistencia().apariencia()

fun EstadoProgramacion.apariencia(): AparienciaEstado = when (this) {
    EstadoProgramacion.PROGRAMADA -> AparienciaEstado("Programada", TonoEstado.INFORMATIVO, SigehIcons.Calendar)
    EstadoProgramacion.CANCELADA -> AparienciaEstado("Cancelada", TonoEstado.NEUTRO, SigehIcons.Ban)
    EstadoProgramacion.SUSTITUIDA -> AparienciaEstado("Sustituida", TonoEstado.NEUTRO, SigehIcons.Repeat)
    EstadoProgramacion.CERRADA -> AparienciaEstado("Cerrada", TonoEstado.NEUTRO, SigehIcons.Lock)
}

/** Badge de estado operativo de la jornada (Presente, Ausente, Parcial, Por capturar…). */
@Composable
fun EstadoAsistenciaBadge(
    estado: EstadoAsistencia,
    modifier: Modifier = Modifier,
    capturable: Boolean = false,
) {
    BadgeEstado(estado.apariencia(capturable), modifier.testTag("badge_${estado.name}"))
}

/** Badge de estado de la programación (Programada, Cancelada, Sustituida, Cerrada). */
@Composable
fun EstadoAsistenciaBadge(
    estado: EstadoProgramacion,
    modifier: Modifier = Modifier,
) {
    BadgeEstado(estado.apariencia(), modifier.testTag("badge_programacion_${estado.name}"))
}

/** Badge con borde, ícono y texto. [texto] opcional reemplaza el de la apariencia (p. ej. con un conteo). */
@Composable
fun BadgeEstado(
    apariencia: AparienciaEstado,
    modifier: Modifier = Modifier,
    texto: String = apariencia.texto,
) {
    val colores = SigehThemeExtras.estados.para(apariencia.tono)
    Row(
        modifier = modifier
            .background(colores.fondo, SigehShapes.Chip)
            .border(BorderStroke(1.dp, colores.borde), SigehShapes.Chip)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(apariencia.icono, contentDescription = null, tint = colores.texto, modifier = Modifier.size(14.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = colores.texto, maxLines = 1)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun EstadoAsistenciaBadgePreview() {
    SigehTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstadoAsistenciaBadge(EstadoAsistencia.PRESENTE)
                EstadoAsistenciaBadge(EstadoAsistencia.AUSENTE)
                EstadoAsistenciaBadge(EstadoAsistencia.PARCIAL)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstadoAsistenciaBadge(EstadoAsistencia.PENDIENTE, capturable = true)
                EstadoAsistenciaBadge(EstadoAsistencia.PENDIENTE)
                EstadoAsistenciaBadge(EstadoAsistencia.NO_EXIGIBLE)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstadoProgramacion.entries.forEach { EstadoAsistenciaBadge(it) }
            }
        }
    }
}
