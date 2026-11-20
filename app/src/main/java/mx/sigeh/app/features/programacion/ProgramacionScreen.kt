package mx.sigeh.app.features.programacion

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.sigeh.app.core.designsystem.components.AccionBloqueo
import mx.sigeh.app.core.designsystem.components.BloqueoSesionTerminada
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoAsistenciaBadge
import mx.sigeh.app.core.designsystem.components.EstadoBloqueo
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.EstadoProgramacion

@Composable
fun ProgramacionRoute(
    onCambiarServicio: () -> Unit,
    onIniciarSesion: () -> Unit,
    viewModel: ProgramacionViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramacionScreen(
        estado = estado,
        onSemanaAnterior = viewModel::onSemanaAnterior,
        onSemanaSiguiente = viewModel::onSemanaSiguiente,
        onDia = viewModel::onDiaSeleccionado,
        onReintentar = viewModel::onReintentar,
        onCambiarServicio = onCambiarServicio,
        onIniciarSesion = onIniciarSesion,
    )
}

/** Pantalla 9.8 · Programación semanal del servicio. */
@Composable
fun ProgramacionScreen(
    estado: ProgramacionUiState,
    onSemanaAnterior: () -> Unit,
    onSemanaSiguiente: () -> Unit,
    onDia: (String) -> Unit,
    onReintentar: () -> Unit,
    onCambiarServicio: () -> Unit,
    onIniciarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SigehTopBar(
            titulo = "Programación",
            subtitulo = estado.servicio.ifBlank { null },
            contenidoInferior = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSemanaAnterior) {
                        Icon(SigehIcons.ChevronLeft, contentDescription = "Semana anterior", tint = SigehColors.Tinta)
                    }
                    Text(
                        estado.semana,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("programacion_semana"),
                    )
                    IconButton(onClick = onSemanaSiguiente) {
                        Icon(SigehIcons.ChevronRight, contentDescription = "Semana siguiente", tint = SigehColors.Tinta)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    estado.dias.forEach { d ->
                        FilterChip(
                            selected = d.seleccionado,
                            onClick = { onDia(d.fecha) },
                            label = { Text(if (estado.cargando) d.texto else "${d.texto} · ${d.jornadas}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SigehColors.PrimaryContainer,
                                selectedLabelColor = SigehColors.OnPrimaryContainer,
                            ),
                        )
                    }
                }
            },
        )
        when {
            estado.bloqueo != null -> Box(Modifier.padding(16.dp)) {
                when (estado.bloqueo) {
                    BloqueoProgramacion.SIN_PERMISO -> EstadoBloqueo(
                        icono = SigehIcons.ShieldX,
                        titulo = "Sin permiso para ver la programación",
                        texto = "Este servicio no tiene la facultad de consultar programación en tu sesión.",
                        accion = AccionBloqueo("Elegir servicio", onCambiarServicio),
                        tono = TonoEstado.NEGATIVO,
                    )
                    BloqueoProgramacion.NO_HABILITADA -> EstadoBloqueo(
                        icono = SigehIcons.CloudOff,
                        titulo = "Programación no disponible en este servidor",
                        texto = "Las lecturas de programación no están habilitadas. Vuelve a intentar más tarde.",
                        accion = AccionBloqueo("Reintentar", onReintentar),
                    )
                    BloqueoProgramacion.SESION_TERMINADA -> BloqueoSesionTerminada(onIniciarSesion)
                }
            }
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    InfoCallout(texto = "Muestra lo programado, no la asistencia ni la cobertura real.", tono = TonoEstado.INFORMATIVO)
                }
                if (!estado.nombresDisponibles) {
                    item {
                        InfoCallout(texto = "Tu sesión no puede consultar el personal: se muestra el final del identificador de cada trabajador.")
                    }
                }
                if (estado.error) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoCallout(texto = "No se pudo cargar la programación.", tono = TonoEstado.NEGATIVO)
                            SigehOutlinedButton("Reintentar", onClick = onReintentar)
                        }
                    }
                } else if (estado.grupos.isEmpty()) {
                    item { EstadoVacio("No hay jornadas programadas para este día.") }
                }
                items(estado.grupos, key = { it.turno }) { grupo ->
                    SigehCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SeccionTitulo("Turno ${grupo.turno} · ${grupo.filas.size}")
                            grupo.filas.forEach { fila ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(fila.nombre, style = MaterialTheme.typography.titleSmall)
                                        Text(fila.horario, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                                    }
                                    EstadoAsistenciaBadge(fila.estado)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ProgramacionPreview() {
    SigehTheme {
        ProgramacionScreen(
            estado = ProgramacionUiState(
                cargando = false,
                servicio = "Medicina Interna",
                semana = "16–22 nov 2026",
                dias = listOf(DiaSemanaUi("2026-11-16", "Lun 16", 6, true), DiaSemanaUi("2026-11-17", "Mar 17", 6, false)),
                grupos = listOf(
                    GrupoTurno(
                        "TM",
                        listOf(
                            FilaProgramacion("1", "Ana Rosales", "07:00–14:30", EstadoProgramacion.PROGRAMADA),
                            FilaProgramacion("2", "Trabajador ··· abcd", "07:00–14:30", EstadoProgramacion.CANCELADA),
                        ),
                    ),
                ),
            ),
            onSemanaAnterior = {},
            onSemanaSiguiente = {},
            onDia = {},
            onReintentar = {},
            onCambiarServicio = {},
            onIniciarSesion = {},
        )
    }
}
