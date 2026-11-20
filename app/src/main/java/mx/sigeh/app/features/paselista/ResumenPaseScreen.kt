package mx.sigeh.app.features.paselista

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.designsystem.theme.tabular

/** Pantalla 6 · Resumen del pase y cobertura (solo lectura). */
@Composable
fun ResumenPaseScreen(
    estado: ResumenPaseUiState,
    onRegresar: () -> Unit,
    onAbrirConflicto: (String) -> Unit,
    onVerSinCapturar: () -> Unit,
    onAbrirRecepcion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SigehTopBar(
            titulo = "Resumen del pase",
            subtitulo = estado.subtitulo,
            onRegresar = onRegresar,
            descripcionRegresar = "Volver al pase de lista",
        )
        if (estado.cargando) {
            CargandoPantalla(Modifier.weight(1f))
        } else {
            ContenidoResumen(estado, onAbrirConflicto, onVerSinCapturar, onAbrirRecepcion, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContenidoResumen(
    estado: ResumenPaseUiState,
    onAbrirConflicto: (String) -> Unit,
    onVerSinCapturar: () -> Unit,
    onAbrirRecepcion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TarjetaPasos(estado.pasos)
        CuadriculaConteo(estado.conteos)

        if (estado.conflictos.isNotEmpty() || estado.sinCapturar > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeccionTitulo("Por atender")
                estado.conflictos.forEach { conflicto ->
                    AvisoEnlace(
                        tono = TonoEstado.NEGATIVO,
                        negrita = "${conflicto.nombre}:",
                        texto = " tu captura no coincide con la del servidor.",
                        onClick = { onAbrirConflicto(conflicto.clientEventId) },
                    )
                }
                if (estado.sinCapturar > 0) {
                    val personas = if (estado.sinCapturar == 1) "persona" else "personas"
                    AvisoEnlace(
                        tono = TonoEstado.ADVERTENCIA,
                        negrita = "${estado.sinCapturar} $personas sin capturar.",
                        texto = " Supervisión no puede cerrar el balance con pendientes.",
                        onClick = onVerSinCapturar,
                    )
                }
            }
        }

        if (estado.cobertura.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeccionTitulo("Cobertura del servicio · solo lectura")
                SigehCard {
                    estado.cobertura.forEachIndexed { indice, fila ->
                        if (indice > 0) HorizontalDivider(color = SigehColors.SurfaceVariant)
                        FilaCoberturaItem(fila, onAbrirRecepcion)
                    }
                }
            }
        }
        Text(
            "El balance diario, su cierre y el reporte PDF se gestionan en la web.",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
            color = SigehColors.OnSurfaceVariant,
        )
    }
}

@Composable
private fun TarjetaPasos(pasos: List<PasoEnvio>) {
    SigehCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Estado del envío", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pasos.forEach { paso ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                    ) {
                        IconoPaso(paso.estado)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                paso.titulo,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (paso.estado == EstadoPaso.PENDIENTE) SigehColors.OnSurfaceVariant else SigehColors.OnSurface,
                            )
                            Text(paso.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconoPaso(estado: EstadoPaso) {
    val advertencia = SigehThemeExtras.estados.advertencia.texto
    when (estado) {
        EstadoPaso.COMPLETO -> Box(
            Modifier
                .size(28.dp)
                .background(SigehColors.Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(SigehIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        EstadoPaso.EN_CURSO -> Box(
            Modifier
                .size(28.dp)
                .border(2.dp, advertencia, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(SigehIcons.Clock, contentDescription = null, tint = advertencia, modifier = Modifier.size(16.dp)) }
        EstadoPaso.PENDIENTE -> Box(
            Modifier
                .size(28.dp)
                .border(2.dp, SigehColors.Outline, CircleShape),
        )
    }
}

@Composable
private fun CuadriculaConteo(conteos: List<ConteoResumen>) {
    val estados = SigehThemeExtras.estados
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        conteos.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { conteo ->
                    val color = when (conteo.tono) {
                        TonoEstado.POSITIVO -> estados.positivo.solido
                        TonoEstado.NEGATIVO -> estados.negativo.solido
                        TonoEstado.ADVERTENCIA -> estados.advertencia.texto
                        else -> SigehColors.OnSurface
                    }
                    Surface(
                        shape = SigehShapes.Aviso,
                        color = Color.White,
                        border = BorderStroke(1.dp, SigehColors.OutlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {},
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                conteo.valor.toString(),
                                style = MaterialTheme.typography.headlineSmall.tabular(),
                                color = color,
                            )
                            Text(conteo.etiqueta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = SigehColors.OnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvisoEnlace(tono: TonoEstado, negrita: String, texto: String, onClick: () -> Unit) {
    val colores = SigehThemeExtras.estados.para(tono)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colores.fondo, SigehShapes.Aviso)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (tono == TonoEstado.NEGATIVO) SigehIcons.AlertTriangle else SigehIcons.Clock,
            contentDescription = null,
            tint = colores.texto,
            modifier = Modifier.size(20.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(negrita) }
                append(texto)
            },
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoFuerte,
            modifier = Modifier.weight(1f),
        )
        Icon(SigehIcons.ChevronRight, contentDescription = null, tint = colores.texto, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FilaCoberturaItem(fila: FilaCobertura, onAbrirRecepcion: (String) -> Unit) {
    val asignacion = fila.asignacionId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (asignacion != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Recibir personal asignado") {
                        onAbrirRecepcion(asignacion)
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fila.titulo, style = MaterialTheme.typography.titleSmall)
            Text(fila.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
        }
        StatusPill(fila.estadoTexto, fila.tono)
        if (asignacion != null) {
            Icon(SigehIcons.ChevronRight, contentDescription = null, tint = SigehColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ResumenPaseScreenPreview() {
    SigehTheme {
        ResumenPaseScreen(
            estado = resumenDeEjemplo(),
            onRegresar = {},
            onAbrirConflicto = {},
            onVerSinCapturar = {},
            onAbrirRecepcion = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, name = "Cargando")
@Composable
private fun ResumenPaseCargandoPreview() {
    SigehTheme {
        Box(Modifier.fillMaxWidth()) {
            ResumenPaseScreen(ResumenPaseUiState(), {}, {}, {}, {})
        }
    }
}
