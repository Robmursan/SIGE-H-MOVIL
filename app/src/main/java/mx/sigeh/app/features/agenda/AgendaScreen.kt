package mx.sigeh.app.features.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.components.SyncPendingChip
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.designsystem.theme.tabular
import mx.sigeh.app.core.model.Agenda
import mx.sigeh.app.core.model.DiaSemana
import mx.sigeh.app.core.model.TipoDiaAgenda

/** Pantalla 3 · Agenda / Mi turno. */
@Composable
fun AgendaScreen(
    estado: AgendaUiState,
    onAbrirSincronizacion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SigehTopBar(
            titulo = "Agenda",
            subtitulo = estado.subtitulo,
            acciones = { SyncPendingChip(porEnviar = estado.porEnviar, onClick = onAbrirSincronizacion) },
        )
        val agenda = estado.agenda
        when {
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            agenda == null -> EstadoVacio("No hay programación guardada para este servicio y turno.")
            else -> ContenidoAgenda(agenda, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ContenidoAgenda(agenda: Agenda, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TarjetaHoy(agenda)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SeccionTitulo(agenda.semanaTitulo)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                agenda.semana.forEach { dia -> CeldaDia(dia, Modifier.weight(1f)) }
            }
            Text(agenda.leyenda, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = SigehColors.OnSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SeccionTitulo("Próximos días")
            SigehCard {
                agenda.proximos.forEachIndexed { indice, dia ->
                    if (indice > 0) HorizontalDivider(color = SigehColors.SurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(dia.fecha, style = MaterialTheme.typography.labelLarge)
                            Text(dia.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                        }
                        StatusPill(
                            dia.etiqueta,
                            when (dia.tipo) {
                                TipoDiaAgenda.LABORAL -> TonoEstado.POSITIVO
                                TipoDiaAgenda.FESTIVO -> TonoEstado.INFORMATIVO
                                TipoDiaAgenda.DESCANSO -> TonoEstado.NEUTRO
                            },
                        )
                    }
                }
            }
        }

        InfoCallout(
            texto = buildString {
                agenda.copiaGuardadaMillis?.let { append("Copia guardada en el teléfono a las ${FormatoHora.hora(it)}. ") }
                append("Sin conexión puede no reflejar convenios o cambios de descanso recientes.")
            },
        )
    }
}

@Composable
private fun TarjetaHoy(agenda: Agenda) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SigehColors.Primary, SigehShapes.TarjetaPrincipal)
            .padding(20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            agenda.hoyEtiqueta,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = SigehColors.HeroEtiqueta,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(agenda.horario, style = MaterialTheme.typography.headlineMedium.tabular(), color = Color.White)
            Text(agenda.turnoServicio, style = MaterialTheme.typography.bodyMedium, color = SigehColors.HeroTexto)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChipHero("Programación publicada · versión ${agenda.programacionVersion}", Color.White, SigehColors.OnPrimaryContainer)
            ChipHero("Descanso semanal: ${agenda.descansoSemanal}", SigehColors.HeroChip, Color.White)
        }
    }
}

@Composable
private fun ChipHero(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(fondo, SigehShapes.Chip)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun CeldaDia(dia: DiaSemana, modifier: Modifier = Modifier) {
    val fondo = when {
        dia.esHoy -> SigehColors.PrimaryContainer
        dia.tipo == TipoDiaAgenda.DESCANSO -> SigehColors.SurfaceVariant
        else -> Color.White
    }
    val descripcion = buildString {
        append("${dia.dia} ${dia.numero}")
        if (dia.esHoy) append(", hoy")
        append(if (dia.tipo == TipoDiaAgenda.DESCANSO) ", descanso" else ", matutino")
    }
    Column(
        modifier = modifier
            .background(fondo, SigehShapes.Campo)
            .border(2.dp, if (dia.esHoy) SigehColors.Primary else SigehColors.OutlineVariant, SigehShapes.Campo)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(dia.dia, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = SigehColors.OnSurfaceVariant)
        Text(dia.numero.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold).tabular())
        Text(
            dia.clave,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (dia.tipo == TipoDiaAgenda.DESCANSO) SigehColors.OnSurfaceVariant else SigehColors.OnPrimaryContainer,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AgendaScreenPreview() {
    SigehTheme { AgendaScreen(estado = agendaDeEjemplo(), onAbrirSincronizacion = {}) }
}
