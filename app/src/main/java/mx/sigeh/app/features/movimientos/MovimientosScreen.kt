package mx.sigeh.app.features.movimientos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.EtiquetaTipo
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehSegmentedTabs
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.components.SyncPendingChip
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.ConvenioParaMi
import mx.sigeh.app.core.model.RespuestaBorrador

/** Pantalla 7 · Movimientos y acuse de convenio. */
@Composable
fun MovimientosScreen(
    estado: MovimientosUiState,
    onAbrirSincronizacion: () -> Unit,
    onFiltro: (FiltroMovimientos) -> Unit,
    onResponder: (movimientoId: String, aceptar: Boolean) -> Unit,
    onRegistrarPaseSalida: () -> Unit,
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
                titulo = "Movimientos",
                acciones = { SyncPendingChip(porEnviar = estado.porEnviar, onClick = onAbrirSincronizacion) },
                contenidoInferior = {
                    SigehSegmentedTabs(
                        opciones = listOf("Del servicio", "Para mí · ${estado.convenios.size}"),
                        seleccionado = estado.filtro.ordinal,
                        onSeleccionar = { onFiltro(FiltroMovimientos.entries[it]) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
            if (estado.cargando) {
                CargandoPantalla(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (estado.filtro) {
                        FiltroMovimientos.DEL_SERVICIO -> {
                            if (estado.movimientos.isEmpty()) item { EstadoVacio("No hay movimientos en este servicio.") }
                            items(estado.movimientos, key = { it.id }) { TarjetaMovimiento(it) }
                            item {
                                Text(
                                    "Las autorizaciones de convenios y cambios de descanso se hacen en la web, " +
                                        "según las facultades de cada rol.",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
                                    color = SigehColors.OnSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        FiltroMovimientos.PARA_MI -> {
                            if (estado.convenios.isEmpty()) item { EstadoVacio("No tienes solicitudes pendientes.") }
                            items(estado.convenios, key = { it.movimientoId }) { convenio ->
                                TarjetaConvenio(
                                    convenio = convenio,
                                    respondiendo = estado.respondiendo == convenio.movimientoId,
                                    onResponder = { aceptar -> onResponder(convenio.movimientoId, aceptar) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (estado.puedeRegistrarSalida) {
            ExtendedFloatingActionButton(
                onClick = onRegistrarPaseSalida,
                icon = { Icon(SigehIcons.LogOut, contentDescription = null) },
                text = { Text("Registrar pase de salida", style = MaterialTheme.typography.labelLarge) },
                containerColor = SigehColors.Primary,
                contentColor = Color.White,
                shape = SigehShapes.TarjetaDestacada,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp),
            )
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }
}

@Composable
private fun TarjetaMovimiento(movimiento: MovimientoUi) {
    SigehCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtiquetaTipo(movimiento.tipo, Modifier.weight(1f))
                StatusPill(movimiento.estadoTexto, movimiento.tono)
            }
            Text(movimiento.titulo, style = MaterialTheme.typography.labelLarge)
            Text(movimiento.detalle, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = SigehColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun TarjetaConvenio(convenio: ConvenioParaMi, respondiendo: Boolean, onResponder: (Boolean) -> Unit) {
    SigehCard(forma = SigehShapes.TarjetaDestacada, borde = BorderStroke(2.dp, SigehColors.Primary)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusPill("CONVENIO DE SUSTITUCIÓN", TonoEstado.INFORMATIVO)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${convenio.solicitante} te pide cubrir su turno",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "${convenio.fecha} · ${convenio.turno} · ${convenio.servicio}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = SigehColors.Tinta,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DatoConvenio("Tipo", convenio.tipoConvenio, Modifier.weight(1f))
                DatoConvenio("Jefatura", convenio.estadoJefatura.etiquetaJefatura(), Modifier.weight(1f))
            }
            Text(
                "Al aceptar, tu programación de ese día se actualiza y ${convenio.solicitante} queda como no exigible.",
                style = MaterialTheme.typography.bodySmall,
                color = SigehColors.OnSurfaceVariant,
            )
            convenio.respuestaBorrador?.let { borrador ->
                InfoCallout(
                    titulo = "Borrador sin enviar",
                    texto = "Guardaste \"${if (borrador == RespuestaBorrador.ACEPTAR) "Aceptar" else "Rechazar"}\" sin conexión. " +
                        "Solo cuenta cuando la recibe el servidor: confírmala de nuevo al tener red.",
                    tono = TonoEstado.ADVERTENCIA,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SigehOutlinedButton(
                    texto = "Rechazar",
                    onClick = { onResponder(false) },
                    habilitado = !respondiendo,
                    color = SigehColors.Error,
                    modifier = Modifier.weight(1f),
                )
                SigehPrimaryButton(
                    texto = "Aceptar",
                    onClick = { onResponder(true) },
                    habilitado = !respondiendo,
                    cargando = respondiendo,
                    alto = 48.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(SigehIcons.Info, contentDescription = null, tint = SigehColors.OnSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    "Tu respuesta solo cuenta cuando la recibe el servidor. Sin conexión se guarda como borrador.",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
                    color = SigehColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DatoConvenio(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = SigehColors.OnSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleSmall)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Del servicio")
@Composable
private fun MovimientosServicioPreview() {
    SigehTheme {
        MovimientosScreen(movimientosDeEjemplo(), {}, {}, { _, _ -> }, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Para mí")
@Composable
private fun MovimientosParaMiPreview() {
    SigehTheme {
        Box(Modifier.height(844.dp).fillMaxWidth()) {
            MovimientosScreen(movimientosDeEjemplo(FiltroMovimientos.PARA_MI), {}, {}, { _, _ -> }, {}, {})
        }
    }
}
