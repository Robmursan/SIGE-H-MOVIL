package mx.sigeh.app.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Íconos vectoriales propios con los mismos trazos que los mockups aprobados (estilo de línea,
 * 24×24, trazo 2). Sustituyen a material-icons-extended, que quedó congelado en 1.7.8.
 * El color lo aplica `Icon` mediante `tint`.
 */
object SigehIcons {
    val ClipboardCheck = icono(
        "ClipboardCheck",
        "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
        "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
        "m9 14 2 2 4-4",
    )
    val Lock = icono(
        "Lock",
        "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z",
        "M8 11V7a4 4 0 0 1 8 0v4",
    )
    val LogOut = icono("LogOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9")
    val Hospital = icono(
        "Hospital",
        "M3 21h18",
        "M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16",
        "M12 7v6",
        "M9 10h6",
    )
    val Check = icono("Check", "M20 6 9 17l-5-5")
    val Clock = icono("Clock", CIRCULO_12_R9, "M12 7v5l3 2")
    val Info = icono("Info", CIRCULO_12_R9, "M12 16v-4", "M12 8h.01")
    val ArrowLeft = icono("ArrowLeft", "M19 12H5", "m12 19-7-7 7-7")
    val ChevronRight = icono("ChevronRight", "m9 18 6-6-6-6")
    val ChevronDown = icono("ChevronDown", "m6 9 6 6 6-6")
    val WifiOff = icono(
        "WifiOff",
        "M12 20h.01",
        "M8.5 16.4a5 5 0 0 1 7 0",
        "M5 12.9a10 10 0 0 1 5.2-2.8",
        "M19 12.9a10 10 0 0 0-2.4-1.7",
        "M2 8.8a15 15 0 0 1 4.2-2.6",
        "M22 8.8A15 15 0 0 0 11 5",
        "m2 2 20 20",
    )
    val CloudCheck = icono(
        "CloudCheck",
        "M17.5 19a4.5 4.5 0 1 0-1.4-8.8A6 6 0 0 0 4 12.5 3.5 3.5 0 0 0 6.5 19z",
        "m9 14 2 2 4-4",
    )
    val AlertTriangle = icono(
        "AlertTriangle",
        "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
        "M12 9v4",
        "M12 17h.01",
    )
    val Repeat = icono(
        "Repeat",
        "m17 2 4 4-4 4",
        "M3 11v-1a4 4 0 0 1 4-4h14",
        "m7 22-4-4 4-4",
        "M21 13v1a4 4 0 0 1-4 4H3",
    )
    val Calendar = icono(
        "Calendar",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M16 2v4",
        "M8 2v4",
        "M3 10h18",
    )
    val RefreshCw = icono(
        "RefreshCw",
        "M21 12a9 9 0 0 1-15.5 6.2L3 16",
        "M3 21v-5h5",
        "M3 12a9 9 0 0 1 15.5-6.2L21 8",
        "M21 3v5h-5",
    )
    val UserCheck = icono(
        "UserCheck",
        "M5 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
        "M2 21a7 7 0 0 1 14 0",
        "m16 11 2 2 4-4",
    )
}

private const val CIRCULO_12_R9 = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0"

private fun icono(nombre: String, vararg trazos: String): ImageVector =
    ImageVector.Builder(
        name = nombre,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        trazos.forEach { trazo ->
            addPath(
                pathData = addPathNodes(trazo),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
