package mx.sigeh.app.features.selectorambito

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.designsystem.theme.tabular

/** Pantalla 2 · Selección de servicio y turno. */
@Composable
fun SelectorAmbitoScreen(
    estado: SelectorAmbitoUiState,
    alcanceVencido: Boolean,
    onServicioSeleccionado: (String) -> Unit,
    onTurnoSeleccionado: (String) -> Unit,
    onContinuar: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmarCierre by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Hola, ${estado.nombreUsuario}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = MaterialTheme.typography.bodyMedium.fontWeight),
                    color = SigehColors.OnSurfaceVariant,
                )
                Text("¿Dónde trabajas hoy?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            IconButton(onClick = { if (estado.pendientesSinEnviar > 0) confirmarCierre = true else onCerrarSesion() }) {
                Icon(SigehIcons.LogOut, contentDescription = "Cerrar sesión", tint = SigehColors.Tinta)
            }
        }

        if (estado.cargando) {
            CargandoPantalla(Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (alcanceVencido) {
                    InfoCallout(
                        titulo = "Tu encargo venció",
                        texto = "Se borró del teléfono lo capturado en ese ámbito. Elige un servicio vigente.",
                        tono = TonoEstado.ADVERTENCIA,
                    )
                }
                if (estado.sinAsignaciones) {
                    InfoCallout(
                        titulo = "Sin ámbitos vigentes",
                        texto = "Tu usuario no tiene asignaciones vigentes en SIGE-H. Acude con Coordinación de enfermería.",
                        tono = TonoEstado.ADVERTENCIA,
                    )
                } else {
                    TarjetaHospital(estado.hospitalNombre, estado.hospitalDetalle)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SeccionTitulo("Servicio")
                    estado.servicios.forEach { servicio ->
                        TarjetaServicio(servicio, onClick = { onServicioSeleccionado(servicio.alcanceId) })
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SeccionTitulo("Turno")
                    estado.turnos.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            fila.forEach { turno ->
                                TarjetaTurno(turno, onClick = { onTurnoSeleccionado(turno.id) }, modifier = Modifier.weight(1f))
                            }
                            if (fila.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    "Solo aparecen los servicios donde tienes nombramiento o un encargo vigente. " +
                        "Como encargado de turno tendrás menos facultades que el titular.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SigehColors.OnSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = SigehColors.OutlineVariant)
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        ) {
            SigehPrimaryButton(
                texto = "Continuar al pase de lista",
                onClick = onContinuar,
                habilitado = estado.puedeContinuar && !estado.cerrandoSesion,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmarCierre) {
        AlertDialog(
            onDismissRequest = { confirmarCierre = false },
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Hay ${estado.pendientesSinEnviar} registros sin enviar en este teléfono. " +
                        "Al cerrar sesión se borran y no llegarán al servidor.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarCierre = false
                    onCerrarSesion()
                }) { Text("Cerrar sesión", color = SigehColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmarCierre = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TarjetaHospital(nombre: String, detalle: String) {
    SigehCard {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(SigehColors.PrimaryContainer, SigehShapes.Campo),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SigehIcons.Hospital, contentDescription = null, tint = SigehColors.OnPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(nombre, style = MaterialTheme.typography.labelLarge)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TarjetaServicio(servicio: OpcionServicio, onClick: () -> Unit) {
    Surface(
        selected = servicio.seleccionado,
        onClick = onClick,
        shape = SigehShapes.Tarjeta,
        color = if (servicio.seleccionado) SigehColors.SeleccionSuave else Color.White,
        border = BorderStroke(2.dp, if (servicio.seleccionado) SigehColors.Primary else SigehColors.OutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.RadioButton },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(servicio.nombre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (servicio.seleccionado) {
                    Icon(SigehIcons.Check, contentDescription = null, tint = SigehColors.Primary, modifier = Modifier.size(22.dp))
                }
            }
            Text(servicio.division, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
            StatusPill(servicio.rolTexto, if (servicio.titular) TonoEstado.POSITIVO else TonoEstado.ADVERTENCIA)
        }
    }
}

@Composable
private fun TarjetaTurno(turno: OpcionTurno, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        selected = turno.seleccionado,
        onClick = onClick,
        shape = SigehShapes.Aviso,
        color = if (turno.seleccionado) SigehColors.SeleccionSuave else Color.White,
        border = BorderStroke(2.dp, if (turno.seleccionado) SigehColors.Primary else SigehColors.OutlineVariant),
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics { role = Role.RadioButton },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(turno.nombre, style = MaterialTheme.typography.titleSmall)
            Text(turno.horario, style = MaterialTheme.typography.bodySmall.tabular(), color = SigehColors.OnSurfaceVariant)
        }
    }
}

internal val estadoSelectorEjemplo = SelectorAmbitoUiState(
    cargando = false,
    nombreUsuario = "M. Hernández",
    hospitalNombre = "Hospital A",
    hospitalDetalle = "Único hospital autorizado para tu usuario",
    servicios = listOf(
        OpcionServicio("alc-mi", "Medicina Interna", "División de Medicina", "Jefa de servicio · titular", true, true),
        OpcionServicio("alc-cg", "Cirugía General", "División de Cirugía", "Encargada de turno · facultades limitadas", false, false),
    ),
    turnos = listOf(
        OpcionTurno("tur-mat", "Matutino", "07:00 – 14:30", true),
        OpcionTurno("tur-ves", "Vespertino", "14:00 – 21:00", false),
        OpcionTurno("tur-noc", "Guardia nocturna", "20:00 – 07:30", false),
        OpcionTurno("tur-ja", "Jornada acumulada", "07:00 – 19:00 · 09:00 – 21:00", false),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SelectorAmbitoScreenPreview() {
    SigehTheme {
        SelectorAmbitoScreen(
            estado = estadoSelectorEjemplo,
            alcanceVencido = false,
            onServicioSeleccionado = {},
            onTurnoSeleccionado = {},
            onContinuar = {},
            onCerrarSesion = {},
        )
    }
}
