package mx.sigeh.app.features.incorporacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.BarraAcciones
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.ConnectionBanner
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.FilaDato
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTextField
import mx.sigeh.app.core.designsystem.components.SigehTimeField
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.SigehThemeExtras
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Pantalla 9 · Recepción de personal eventual (solo en línea). */
@Composable
fun RecepcionEventualScreen(
    estado: RecepcionEventualUiState,
    onRegresar: () -> Unit,
    onInicioEfectivo: (String) -> Unit,
    onConfirmar: () -> Unit,
    onMostrarRechazo: (Boolean) -> Unit,
    onMotivoRechazo: (String) -> Unit,
    onRechazar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SigehTopBar(
            titulo = "Recepción de eventual",
            subtitulo = estado.subtitulo.takeIf { it.isNotBlank() },
            onRegresar = onRegresar,
            descripcionRegresar = "Volver al resumen",
        )
        ConnectionBanner(
            enLinea = estado.enLinea,
            textoEnLinea = "En línea. Esta acción solo se puede hacer con conexión.",
            textoSinConexion = "Sin conexión. Esta acción solo se puede hacer con conexión.",
        )
        val asignacion = estado.asignacion
        when {
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            estado.noEncontrada || asignacion == null -> Column(Modifier.weight(1f)) {
                EstadoVacio("No se encontró la asignación.")
            }
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SigehCard(forma = SigehShapes.TarjetaDestacada) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val info = SigehThemeExtras.estados.informativo
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(info.fondo, CircleShape)
                                    .clearAndSetSemantics {},
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(asignacion.persona.iniciales, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = info.texto)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    asignacion.persona.nombre,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    "${asignacion.persona.categoria} · eventual · RUD ···${asignacion.persona.rudUltimos4}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SigehColors.OnSurfaceVariant,
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            asignacion.persona.competenciasVerificadas.forEach {
                                StatusPill("$it · verificada", TonoEstado.NEUTRO, icono = SigehIcons.Check)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                            FilaDato("Necesidad", asignacion.necesidad.titulo)
                            FilaDato("Horario a cubrir", asignacion.necesidad.horario)
                            FilaDato("Llegó a Coordinación", asignacion.llegadaCoordinacion)
                            FilaDato("Asignada por Coordinación", asignacion.asignadaA)
                        }
                    }
                }

                if (estado.yaIncorporada) {
                    InfoCallout(
                        texto = "Incorporación confirmada a las ${asignacion.incorporadaA}.",
                        tono = TonoEstado.POSITIVO,
                        icono = SigehIcons.UserCheck,
                    )
                } else {
                    SigehTimeField(
                        etiqueta = "Inicio efectivo en el servicio",
                        hora = estado.inicioEfectivo,
                        onHoraChange = onInicioEfectivo,
                        habilitado = estado.accionesHabilitadas,
                        grande = true,
                    )
                }
                InfoCallout(
                    texto = "La necesidad cuenta como cubierta solo cuando confirmas aquí que la persona llegó al servicio. " +
                        "Ver la asignación no basta.",
                )
                estado.motivoBloqueo?.let { InfoCallout(texto = it, tono = TonoEstado.ADVERTENCIA) }
                estado.error?.let { InfoCallout(texto = it, tono = TonoEstado.NEGATIVO) }
            }
        }
        if (!estado.yaIncorporada && asignacion != null) {
            BarraAcciones {
                SigehPrimaryButton(
                    texto = "Confirmar incorporación",
                    onClick = onConfirmar,
                    habilitado = estado.accionesHabilitadas,
                    cargando = estado.enviando,
                    icono = SigehIcons.UserCheck,
                    modifier = Modifier.fillMaxWidth(),
                )
                SigehOutlinedButton(
                    texto = "Rechazar con motivo",
                    onClick = { onMostrarRechazo(true) },
                    habilitado = estado.accionesHabilitadas,
                    color = SigehColors.Error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (estado.mostrarRechazo) {
        AlertDialog(
            onDismissRequest = { onMostrarRechazo(false) },
            title = { Text("Rechazar incorporación") },
            text = {
                SigehTextField(
                    etiqueta = "Motivo",
                    valor = estado.motivoRechazo,
                    onValorChange = onMotivoRechazo,
                    marcador = "Por ejemplo: no se presentó en el servicio",
                    multilinea = true,
                )
            },
            confirmButton = {
                TextButton(onClick = onRechazar, enabled = estado.motivoRechazo.isNotBlank()) {
                    Text("Rechazar", color = if (estado.motivoRechazo.isNotBlank()) SigehColors.Error else SigehColors.OnSurfaceVariant)
                }
            },
            dismissButton = { TextButton(onClick = { onMostrarRechazo(false) }) { Text("Cancelar") } },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900, name = "En línea")
@Composable
private fun RecepcionEventualPreview() {
    SigehTheme { RecepcionEventualScreen(recepcionDeEjemplo(), {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900, name = "Sin conexión")
@Composable
private fun RecepcionEventualSinConexionPreview() {
    SigehTheme { RecepcionEventualScreen(recepcionDeEjemplo(enLinea = false), {}, {}, {}, {}, {}, {}) }
}
