package mx.sigeh.app.features.incidencias

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.EtiquetaTipo
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.components.SyncPendingChip
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.features.sincronizacion.RegistroSyncUi

/** Pestaña Incidencias (pantalla adicional): registradas hoy y personal para registrar una nueva. */
@Composable
fun IncidenciasScreen(
    estado: IncidenciasUiState,
    onAbrirSincronizacion: () -> Unit,
    onRegistrar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SigehTopBar(
            titulo = "Incidencias",
            subtitulo = estado.subtitulo.takeIf { it.isNotBlank() },
            acciones = { SyncPendingChip(porEnviar = estado.porEnviar, onClick = onAbrirSincronizacion) },
        )
        when {
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            estado.sinProgramacion -> EstadoVacio("No hay personal programado para este servicio y turno.")
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeccionTitulo("Registradas en este teléfono")
                    if (estado.registradas.isEmpty()) {
                        Text(
                            "Aún no registras incidencias en este turno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SigehColors.OnSurfaceVariant,
                        )
                    }
                    estado.registradas.forEach { TarjetaIncidencia(it) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeccionTitulo("Registrar incidencia para")
                    SigehCard {
                        estado.jornadas.forEachIndexed { indice, jornada ->
                            if (indice > 0) HorizontalDivider(color = SigehColors.SurfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(
                                        enabled = estado.puedeRegistrar,
                                        role = Role.Button,
                                        onClickLabel = "Registrar incidencia",
                                    ) { onRegistrar(jornada.programacionId) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(jornada.nombre, style = MaterialTheme.typography.titleMedium)
                                    Text(jornada.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                                }
                                Icon(SigehIcons.ChevronRight, contentDescription = null, tint = SigehColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                InfoCallout(
                    texto = "Desde el teléfono solo se registran incidencias preliminares. " +
                        "Supervisión o Recursos Humanos las clasifican y autorizan en la web.",
                )
            }
        }
    }
}

@Composable
private fun TarjetaIncidencia(registro: RegistroSyncUi) {
    SigehCard(forma = SigehShapes.Aviso) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EtiquetaTipo(registro.tipo)
                    Text(registro.titulo, style = MaterialTheme.typography.labelLarge)
                }
                StatusPill(registro.estado.texto, registro.estado.tono)
            }
            Text(registro.detalle, style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = SigehColors.OnSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IncidenciasScreenPreview() {
    SigehTheme { IncidenciasScreen(incidenciasDeEjemplo(), onAbrirSincronizacion = {}, onRegistrar = {}) }
}
