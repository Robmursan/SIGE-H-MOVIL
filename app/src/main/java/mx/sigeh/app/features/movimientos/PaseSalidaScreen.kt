package mx.sigeh.app.features.movimientos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.components.BarraAcciones
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehSelectField
import mx.sigeh.app.core.designsystem.components.SigehTextField
import mx.sigeh.app.core.designsystem.components.SigehTimeField
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Pantalla 8 · Pase de salida. */
@Composable
fun PaseSalidaScreen(
    estado: PaseSalidaUiState,
    onCancelar: () -> Unit,
    onTrabajador: (TrabajadorProgramado) -> Unit,
    onHoraSalida: (String) -> Unit,
    onAhora: () -> Unit,
    onRegistrarRegreso: (Boolean) -> Unit,
    onHoraRegreso: (String) -> Unit,
    onNota: (String) -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SigehTopBar(titulo = "Pase de salida", onRegresar = onCancelar, descripcionRegresar = "Volver a movimientos")
        if (estado.cargando) {
            CargandoPantalla(Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SigehSelectField(
                    etiqueta = "Trabajador",
                    opciones = estado.trabajadores,
                    seleccion = estado.seleccionado,
                    textoDe = { it.texto },
                    onSeleccion = onTrabajador,
                    ayuda = "Solo personal programado hoy en tu servicio.",
                )

                val pase = estado.paseAutorizado
                if (pase != null) {
                    InfoCallout(
                        texto = "Pase autorizado por la jefatura para hoy a las ${pase.horaAutorizada}.",
                        tono = TonoEstado.POSITIVO,
                        icono = SigehIcons.Check,
                    )
                } else if (estado.seleccionado != null) {
                    InfoCallout(
                        texto = "Esta persona no tiene un pase autorizado hoy. La jefatura debe autorizarlo antes de registrar la salida.",
                        tono = TonoEstado.ADVERTENCIA,
                    )
                }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SigehTimeField("Hora real de salida", estado.horaSalida, onHoraSalida, Modifier.weight(1f), grande = true)
                    SigehOutlinedButton("Ahora", onAhora, alto = 52.dp, forma = SigehShapes.Campo)
                }

                SigehCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.toggleable(
                                value = estado.registrarRegreso,
                                role = Role.Switch,
                                onValueChange = onRegistrarRegreso,
                            ),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Registrar regreso", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Opcional: no siempre se registra",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                                    color = SigehColors.OnSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = estado.registrarRegreso,
                                onCheckedChange = null,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = SigehColors.Primary,
                                    checkedThumbColor = Color.White,
                                    uncheckedTrackColor = SigehColors.SurfaceVariant,
                                    uncheckedThumbColor = SigehColors.Marcador,
                                    uncheckedBorderColor = SigehColors.Marcador,
                                ),
                            )
                        }
                        if (estado.registrarRegreso) {
                            SigehTimeField("Hora de regreso", estado.horaRegreso, onHoraRegreso, etiquetaPequena = true)
                            if (estado.horaRegreso <= estado.horaSalida) {
                                Text(
                                    "El regreso debe ser posterior a la salida.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SigehColors.Error,
                                )
                            }
                        } else {
                            StatusPill("Regreso no verificado", TonoEstado.NEUTRO, icono = SigehIcons.Clock)
                        }
                    }
                }

                SigehTextField(
                    etiqueta = "Nota (opcional)",
                    valor = estado.nota,
                    onValorChange = onNota,
                    marcador = "Sin datos personales sensibles",
                )

                Text(
                    "La salida no genera cobertura por sí sola: Coordinación decide si se cubre por horas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SigehColors.OnSurfaceVariant,
                )

                if (!estado.enLinea) {
                    InfoCallout(
                        titulo = "Requiere conexión",
                        texto = "Registrar la salida efectiva no es una captura preliminar: se hace con red.",
                        tono = TonoEstado.ADVERTENCIA,
                        icono = SigehIcons.WifiOff,
                    )
                }
                estado.error?.let { InfoCallout(texto = it, tono = TonoEstado.NEGATIVO) }
            }
        }
        BarraAcciones {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SigehOutlinedButton("Cancelar", onCancelar, Modifier.weight(1f))
                SigehPrimaryButton(
                    texto = "Registrar salida",
                    onClick = onGuardar,
                    habilitado = estado.puedeGuardar,
                    cargando = estado.guardando,
                    alto = 48.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun PaseSalidaScreenPreview() {
    SigehTheme {
        PaseSalidaScreen(paseSalidaDeEjemplo(), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900, name = "Con regreso, sin conexión")
@Composable
private fun PaseSalidaRegresoPreview() {
    SigehTheme {
        PaseSalidaScreen(paseSalidaDeEjemplo(enLinea = false, registrarRegreso = true), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
