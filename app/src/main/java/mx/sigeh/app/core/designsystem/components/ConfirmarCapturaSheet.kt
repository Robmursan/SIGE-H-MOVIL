package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.EstadoCaptura

const val TEXTO_PRIMERA_CAPTURA_TITULO = "Es la primera y única captura de esta jornada."
const val TEXTO_PRIMERA_CAPTURA = "Después de confirmar no se puede cambiar desde la app."
const val TEXTO_AUSENTE_NO_ES_FALTA =
    "\"Ausente\" es una observación operativa: no genera falta ni incidencia, y queda pendiente de validación administrativa."

/**
 * Confirmación previa a la primera captura (prompt M2 §9.4). Es sin estado: el estado elegido lo
 * guarda quien la muestra, porque la `Idempotency-Key` queda ligada a `(programacionId, estado)`.
 *
 * - Se abre con el estado del botón que se tocó; se puede cambiar antes de confirmar.
 * - Con [enviando] el botón de registro se desactiva y la hoja no se puede cerrar: la intención
 *   ya salió hacia el servidor y su resultado debe mostrarse.
 * - «Cancelar» solo cierra: no envía nada.
 *
 * @param detalle «{turno} · HH:mm–HH:mm · {servicio}», ya formateado en la zona del hospital.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmarCapturaSheet(
    nombre: String,
    detalle: String,
    estado: EstadoCaptura,
    onEstadoChange: (EstadoCaptura) -> Unit,
    enviando: Boolean,
    onConfirmar: (EstadoCaptura) -> Unit,
    onCancelar: () -> Unit,
) {
    val enviandoActual by rememberUpdatedState(enviando)
    val estadoHoja = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { valor -> valor != SheetValue.Hidden || !enviandoActual },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!enviandoActual) onCancelar() },
        sheetState = estadoHoja,
        containerColor = Color.White,
        modifier = Modifier.testTag("confirmar_captura"),
    ) {
        ContenidoConfirmarCaptura(nombre, detalle, estado, onEstadoChange, enviando, onConfirmar, onCancelar)
    }
}

/** Contenido de la hoja, separado para poder verlo en `@Preview` (la hoja es una ventana aparte). */
@Composable
fun ContenidoConfirmarCaptura(
    nombre: String,
    detalle: String,
    estado: EstadoCaptura,
    onEstadoChange: (EstadoCaptura) -> Unit,
    enviando: Boolean,
    onConfirmar: (EstadoCaptura) -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val apariencia = estado.apariencia()
    val colores = SigehThemeExtras.estados.para(apariencia.tono)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Confirmar asistencia",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarIniciales(nombre)
            Column {
                Text(nombre, style = MaterialTheme.typography.titleSmall, color = SigehColors.OnSurface)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
            }
        }
        SelectorEstadoCaptura(seleccionado = estado, onSeleccionar = onEstadoChange, habilitado = !enviando)
        InfoCallout(
            titulo = TEXTO_PRIMERA_CAPTURA_TITULO,
            texto = TEXTO_PRIMERA_CAPTURA,
            tono = TonoEstado.ADVERTENCIA,
        )
        if (estado != EstadoCaptura.PRESENTE) {
            InfoCallout(texto = TEXTO_AUSENTE_NO_ES_FALTA, tono = TonoEstado.INFORMATIVO)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SigehOutlinedButton(
                texto = "Cancelar",
                onClick = onCancelar,
                habilitado = !enviando,
                alto = 52.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("confirmar_cancelar"),
            )
            SigehPrimaryButton(
                texto = if (enviando) "Enviando…" else "Registrar ${apariencia.texto}",
                onClick = { onConfirmar(estado) },
                cargando = enviando,
                color = colores.solido,
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("confirmar_registrar"),
            )
        }
    }
}

/** Selector segmentado Presente / Ausente / Parcial: el elegido va relleno del color del estado y con su ícono. */
@Composable
private fun SelectorEstadoCaptura(
    seleccionado: EstadoCaptura,
    onSeleccionar: (EstadoCaptura) -> Unit,
    habilitado: Boolean,
) {
    val estados = SigehThemeExtras.estados
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EstadoCaptura.entries.forEach { opcion ->
            val activo = opcion == seleccionado
            val apariencia = opcion.apariencia()
            val colores = estados.para(apariencia.tono)
            Surface(
                selected = activo,
                onClick = { onSeleccionar(opcion) },
                enabled = habilitado,
                shape = SigehShapes.Campo,
                color = if (activo) colores.solido else Color.White,
                contentColor = if (activo) colores.sobreSolido else colores.texto,
                border = BorderStroke(1.5.dp, if (activo) colores.solido else colores.borde),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { role = Role.RadioButton }
                    .testTag("confirmar_estado_${opcion.name}"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(apariencia.icono, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(apariencia.texto, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ConfirmarCapturaPresentePreview() {
    SigehTheme {
        ContenidoConfirmarCaptura(
            nombre = "Jorge Salas",
            detalle = "TM · 07:00–14:30 · Medicina Interna",
            estado = EstadoCaptura.PRESENTE,
            onEstadoChange = {},
            enviando = false,
            onConfirmar = {},
            onCancelar = {},
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ConfirmarCapturaAusenteEnviandoPreview() {
    SigehTheme {
        ContenidoConfirmarCaptura(
            nombre = "Lucía Navarro",
            detalle = "TM · 07:00–14:30 · Medicina Interna",
            estado = EstadoCaptura.AUSENTE,
            onEstadoChange = {},
            enviando = true,
            onConfirmar = {},
            onCancelar = {},
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
