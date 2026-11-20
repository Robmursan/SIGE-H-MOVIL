package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Acción única de un [EstadoBloqueo]. [habilitada] en false mientras corre una cuenta regresiva. */
data class AccionBloqueo(
    val texto: String,
    val onClick: () -> Unit,
    val habilitada: Boolean = true,
)

/**
 * Tarjeta reutilizable para estados que impiden seguir (prompt M2 §9.10): 401, 403, 429, 503 y
 * versión incompatible. Se anuncia como región viva para TalkBack.
 */
@Composable
fun EstadoBloqueo(
    icono: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    accion: AccionBloqueo? = null,
    tono: TonoEstado = TonoEstado.NEUTRO,
) {
    val colores = SigehThemeExtras.estados.para(tono)
    SigehCard(modifier = modifier.testTag("estado_bloqueo")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colores.fondo, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, tint = colores.texto, modifier = Modifier.size(28.dp))
            }
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                color = SigehColors.OnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = SigehColors.OnSurfaceVariant, textAlign = TextAlign.Center)
            if (accion != null) {
                SigehPrimaryButton(
                    texto = accion.texto,
                    onClick = accion.onClick,
                    habilitado = accion.habilitada,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .testTag("estado_bloqueo_accion"),
                )
            }
        }
    }
}

/** 90 → «1:30». Para la cuenta regresiva de un 429. */
fun formatoCuentaRegresiva(segundos: Long): String {
    val s = segundos.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

// --- Estados de acceso del prompt M2 §9.10, con sus textos ---------------------------------------

/** 503 «Asistencia no habilitada»: sin reintento automático, solo manual. */
@Composable
fun BloqueoNoHabilitado(onReintentar: () -> Unit, modifier: Modifier = Modifier) = EstadoBloqueo(
    icono = SigehIcons.CloudOff,
    titulo = "Pase de lista no disponible en este servidor",
    texto = "El módulo de asistencia no está habilitado. Avisa a Coordinación o vuelve a intentar más tarde.",
    accion = AccionBloqueo("Reintentar", onReintentar),
    modifier = modifier,
)

/** 403 «Acceso denegado» en el servicio elegido. */
@Composable
fun BloqueoSinPermiso(onElegirServicio: () -> Unit, modifier: Modifier = Modifier) = EstadoBloqueo(
    icono = SigehIcons.ShieldX,
    titulo = "Sin permiso en este servicio",
    texto = "Tu sesión ya no tiene facultades en este servicio. Elige otro de tus ámbitos autorizados.",
    accion = AccionBloqueo("Elegir servicio", onElegirServicio),
    tono = TonoEstado.NEGATIVO,
    modifier = modifier,
)

/** 401 después de intentar renovar la sesión. */
@Composable
fun BloqueoSesionTerminada(onIniciarSesion: () -> Unit, modifier: Modifier = Modifier) = EstadoBloqueo(
    icono = SigehIcons.Lock,
    titulo = "Tu sesión terminó",
    texto = "Inicia sesión de nuevo para continuar. No se perdió ningún registro confirmado.",
    accion = AccionBloqueo("Iniciar sesión", onIniciarSesion),
    modifier = modifier,
)

/** 429: cuenta regresiva con `Retry-After` (60 s si no viene). La acción se habilita al llegar a cero. */
@Composable
fun BloqueoDemasiadosIntentos(segundosRestantes: Long, onReintentar: () -> Unit, modifier: Modifier = Modifier) =
    EstadoBloqueo(
        icono = SigehIcons.Clock,
        titulo = "Demasiados intentos",
        texto = if (segundosRestantes > 0) {
            "Espera ${formatoCuentaRegresiva(segundosRestantes)} antes de volver a intentar."
        } else {
            "Ya puedes volver a intentar."
        },
        accion = AccionBloqueo("Volver a intentar", onReintentar, habilitada = segundosRestantes <= 0),
        tono = TonoEstado.ADVERTENCIA,
        modifier = modifier,
    )

/** `apiVersion` del servidor no empieza con `1.`. */
@Composable
fun BloqueoVersionIncompatible(modifier: Modifier = Modifier) = EstadoBloqueo(
    icono = SigehIcons.Download,
    titulo = "Actualiza la aplicación",
    texto = "El servidor usa una versión del contrato que esta app no conoce. Instala la versión más reciente para continuar.",
    tono = TonoEstado.INFORMATIVO,
    modifier = modifier,
)

@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@Composable
private fun EstadoBloqueoPreview() {
    SigehTheme {
        Column(
            Modifier
                .background(SigehColors.Background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BloqueoNoHabilitado(onReintentar = {})
            BloqueoSinPermiso(onElegirServicio = {})
            BloqueoSesionTerminada(onIniciarSesion = {})
            BloqueoDemasiadosIntentos(segundosRestantes = 42, onReintentar = {})
            BloqueoVersionIncompatible()
        }
    }
}
