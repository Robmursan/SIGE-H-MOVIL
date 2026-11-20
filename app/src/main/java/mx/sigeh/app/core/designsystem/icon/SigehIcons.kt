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
    val ChevronLeft = icono("ChevronLeft", "m15 18-6-6 6-6")
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

    // --- M2 ------------------------------------------------------------------------------------

    /** Ausente. */
    val UserX = icono(
        "UserX",
        "M5 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
        "M2 21a7 7 0 0 1 14 0",
        "m17 8 5 5",
        "m22 8-5 5",
    )

    /** Por capturar: círculo punteado (todavía sin estado). */
    val CircleDashed = icono(
        "CircleDashed",
        "M10.1 2.18a10 10 0 0 1 3.8 0",
        "M17.6 3.72a10 10 0 0 1 2.7 2.7",
        "M21.82 10.1a10 10 0 0 1 0 3.8",
        "M20.28 17.6a10 10 0 0 1-2.7 2.7",
        "M13.9 21.82a10 10 0 0 1-3.8 0",
        "M6.4 20.28a10 10 0 0 1-2.7-2.7",
        "M2.18 13.9a10 10 0 0 1 0-3.8",
        "M3.72 6.4a10 10 0 0 1 2.7-2.7",
    )

    /** No exigible. */
    val MinusCircle = icono("MinusCircle", CIRCULO_12_R9, "M8 12h8")

    /** Programación cancelada. */
    val Ban = icono("Ban", CIRCULO_12_R9, "m5.6 5.6 12.8 12.8")

    /** Módulo deshabilitado o servidor no disponible (503). */
    val CloudOff = icono(
        "CloudOff",
        "m2 2 20 20",
        "M5.78 5.78A7 7 0 0 0 9 19h8.5a4.5 4.5 0 0 0 1.3-.19",
        "M21.53 16.5A4.5 4.5 0 0 0 17.5 10h-1.79A7 7 0 0 0 10 5.07",
    )

    /** Sin permiso en el servicio (403). */
    val ShieldX = icono(
        "ShieldX",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m14.5 9.5-5 5",
        "m9.5 9.5 5 5",
    )

    /** Actualizar la aplicación (versión incompatible). */
    val Download = icono("Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3")

    /** Cerrar o cancelar. */
    val X = icono("X", "M18 6 6 18", "m6 6 12 12")

    /** Cuenta. */
    val User = icono("User", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0")

    /** Mostrar contraseña. */
    val Eye = icono(
        "Eye",
        "M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    )

    /** Ocultar contraseña. */
    val EyeOff = icono(
        "EyeOff",
        "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
        "M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
        "M14.12 14.12a3 3 0 1 1-4.24-4.24",
        "m2 2 20 20",
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
