package mx.sigeh.app.features.paselista

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.AttendanceSelector
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.ConnectionBanner
import mx.sigeh.app.core.designsystem.components.CounterStrip
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.SyncPendingChip
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.AvisoJornada
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.TonoAviso

/** Pantalla 4 · Pase de lista (pantalla central). */
@Composable
fun PaseListaScreen(
    estado: PaseListaUiState,
    onRegresar: () -> Unit,
    onAbrirSincronizacion: () -> Unit,
    onEstadoSeleccionado: (programacionId: String, estado: EstadoAsistencia) -> Unit,
    onAbrirConflicto: (clientEventId: String) -> Unit,
    onGuardarBorrador: () -> Unit,
    onEnviarAValidar: () -> Unit,
    onMensajeMostrado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        snackbar.showSnackbar(texto)
        onMensajeMostrado()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            SigehTopBar(
                titulo = "Pase de lista",
                onRegresar = onRegresar,
                descripcionRegresar = "Cambiar servicio o turno",
                acciones = { SyncPendingChip(porEnviar = estado.porEnviar, onClick = onAbrirSincronizacion) },
                contenidoInferior = {
                    Text(estado.contexto, style = MaterialTheme.typography.labelLarge)
                    Text(estado.subcontexto, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                },
            )
            ConnectionBanner(enLinea = estado.enLinea)

            when {
                estado.cargando -> CargandoPantalla(Modifier.weight(1f))
                estado.sinProgramacion -> Column(Modifier.weight(1f)) {
                    EstadoVacio("No hay personal programado para este servicio y turno.")
                    Spacer(Modifier.weight(1f))
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lista_pase"),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(estado.filas, key = { it.programacionId }) { fila ->
                        TarjetaFila(
                            fila = fila,
                            enLinea = estado.enLinea,
                            puedeCapturar = estado.puedeCapturar,
                            onSeleccionar = { nuevo ->
                                val conflicto = fila.clientEventId
                                if (fila.indicador == IndicadorFila.CONFLICTO && conflicto != null) {
                                    onAbrirConflicto(conflicto)
                                } else {
                                    onEstadoSeleccionado(fila.programacionId, nuevo)
                                }
                            },
                            onAbrirConflicto = onAbrirConflicto,
                        )
                    }
                }
            }

            PiePase(
                contadores = estado.contadores,
                habilitado = !estado.cargando && !estado.sinProgramacion,
                onGuardarBorrador = onGuardarBorrador,
                onEnviarAValidar = onEnviarAValidar,
            )
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 132.dp),
        )
    }
}

@Composable
private fun TarjetaFila(
    fila: FilaPaseUi,
    enLinea: Boolean,
    puedeCapturar: Boolean,
    onSeleccionar: (EstadoAsistencia) -> Unit,
    onAbrirConflicto: (String) -> Unit,
) {
    SigehCard(Modifier.testTag("fila_${fila.programacionId}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(fila.nombre, style = MaterialTheme.typography.titleMedium)
                    Text(
                        fila.detalle,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = SigehColors.OnSurfaceVariant,
                    )
                }
                IndicadorSync(fila, enLinea, onAbrirConflicto)
            }
            fila.aviso?.let { AvisoChip(it) }
            if (fila.exigible) {
                AttendanceSelector(
                    seleccionado = fila.estado.takeIf { it != EstadoAsistencia.PENDIENTE },
                    onSeleccionar = onSeleccionar,
                    nombre = fila.nombre,
                    habilitado = puedeCapturar,
                )
            } else {
                Text(
                    "No exigible hoy: no requiere captura ni cuenta como falta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SigehColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IndicadorSync(fila: FilaPaseUi, enLinea: Boolean, onAbrirConflicto: (String) -> Unit) {
    val estados = SigehThemeExtras.estados
    when (fila.indicador) {
        IndicadorFila.SINCRONIZADO -> EtiquetaIndicador(SigehIcons.CloudCheck, "Sincronizado", SigehColors.Primary)
        IndicadorFila.EN_TELEFONO -> EtiquetaIndicador(
            SigehIcons.Clock,
            if (enLinea) "Enviando…" else "En el teléfono",
            estados.advertencia.texto,
        )
        IndicadorFila.ENVIANDO -> EtiquetaIndicador(SigehIcons.Clock, "Enviando…", estados.advertencia.texto)
        IndicadorFila.EN_REVISION -> EtiquetaIndicador(SigehIcons.Clock, "En revisión", estados.informativo.texto)
        IndicadorFila.DENEGADO -> EtiquetaIndicador(SigehIcons.Info, "Denegado", estados.neutro.texto)
        IndicadorFila.SIN_CAPTURAR -> Text(
            "Sin capturar",
            style = MaterialTheme.typography.labelMedium,
            color = SigehColors.OnSurfaceVariant,
        )
        IndicadorFila.CONFLICTO -> {
            val id = fila.clientEventId
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = id != null, role = Role.Button, onClickLabel = "Resolver conflicto") {
                        if (id != null) onAbrirConflicto(id)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(SigehIcons.AlertTriangle, contentDescription = null, tint = SigehColors.Error, modifier = Modifier.size(16.dp))
                Text(
                    "Conflicto",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = SigehColors.Error,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
        IndicadorFila.NINGUNO -> Unit
    }
}

@Composable
private fun EtiquetaIndicador(icono: ImageVector, texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
internal fun AvisoChip(aviso: AvisoJornada) {
    val tono = when (aviso.tono) {
        TonoAviso.NEUTRO -> TonoEstado.NEUTRO
        TonoAviso.INFORMATIVO -> TonoEstado.INFORMATIVO
        TonoAviso.ADVERTENCIA -> TonoEstado.ADVERTENCIA
    }
    val colores = SigehThemeExtras.estados.para(tono)
    Text(
        aviso.texto,
        style = MaterialTheme.typography.labelMedium,
        color = colores.texto,
        modifier = Modifier
            .background(colores.fondo, SigehShapes.Chip)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun PiePase(
    contadores: ContadoresPase,
    habilitado: Boolean,
    onGuardarBorrador: () -> Unit,
    onEnviarAValidar: () -> Unit,
) {
    Surface(color = Color.White) {
        Column {
            HorizontalDivider(color = SigehColors.OutlineVariant)
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CounterStrip(
                    programados = contadores.programados,
                    exigibles = contadores.exigibles,
                    capturados = contadores.capturados,
                    pendientes = contadores.pendientes,
                    validados = contadores.validados,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SigehOutlinedButton(
                        texto = "Guardar borrador",
                        onClick = onGuardarBorrador,
                        habilitado = habilitado,
                        modifier = Modifier.weight(1f),
                    )
                    SigehPrimaryButton(
                        texto = "Enviar a validar",
                        onClick = onEnviarAValidar,
                        habilitado = habilitado,
                        alto = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Sin conexión")
@Composable
private fun PaseListaScreenSinConexionPreview() {
    SigehTheme {
        PaseListaScreen(
            estado = paseListaEjemplo(enLinea = false),
            onRegresar = {},
            onAbrirSincronizacion = {},
            onEstadoSeleccionado = { _, _ -> },
            onAbrirConflicto = {},
            onGuardarBorrador = {},
            onEnviarAValidar = {},
            onMensajeMostrado = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "En línea")
@Composable
private fun PaseListaScreenEnLineaPreview() {
    SigehTheme {
        PaseListaScreen(
            estado = paseListaEjemplo(enLinea = true),
            onRegresar = {},
            onAbrirSincronizacion = {},
            onEstadoSeleccionado = { _, _ -> },
            onAbrirConflicto = {},
            onGuardarBorrador = {},
            onEnviarAValidar = {},
            onMensajeMostrado = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun FilaPasePreview() {
    SigehTheme {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            paseListaEjemplo(enLinea = false).filas.take(3).forEach { fila ->
                TarjetaFila(fila, enLinea = false, puedeCapturar = true, onSeleccionar = {}, onAbrirConflicto = {})
            }
        }
    }
}
