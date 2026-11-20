package mx.sigeh.app.features.detallejornada

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.sigeh.app.core.designsystem.components.AvatarIniciales
import mx.sigeh.app.core.designsystem.components.EstadoAsistenciaBadge
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.FilaDato
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoProgramacion

@Composable
fun DetalleJornadaRoute(onRegresar: () -> Unit, viewModel: DetalleJornadaViewModel = hiltViewModel()) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    DetalleJornadaScreen(estado = estado, onRegresar = onRegresar)
}

/** Pantalla 9.7 · Detalle de jornada (solo lectura). */
@Composable
fun DetalleJornadaScreen(estado: DetalleJornadaUiState, onRegresar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SigehTopBar(titulo = "Detalle de jornada", subtitulo = estado.servicio.ifBlank { null }, onRegresar = onRegresar)
        if (!estado.encontrada) {
            EstadoVacio("Esta jornada ya no está en memoria. Vuelve al pase de lista y actualiza.")
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("detalle_jornada"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarIniciales(estado.nombre, tamano = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(estado.nombre, style = MaterialTheme.typography.titleLarge)
                    if (estado.servicio.isNotBlank()) {
                        Text(estado.servicio, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                    }
                }
                estado.estadoOperativo?.let { EstadoAsistenciaBadge(it) }
            }
            if (estado.programacion.isNotEmpty()) Tarjeta("Programación", estado.programacion)
            Tarjeta("Asistencia", estado.asistencia)
            estado.acuse?.let { Tarjeta("Acuse del servidor", it) }
            InfoCallout(texto = "Las correcciones y la validación las hace Coordinación; no se editan desde esta app.")
        }
    }
}

@Composable
private fun Tarjeta(titulo: String, datos: List<DatoDetalle>) {
    SigehCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SeccionTitulo(titulo)
            datos.forEach { FilaDato(it.etiqueta, it.valor) }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DetalleJornadaPreview() {
    SigehTheme {
        DetalleJornadaScreen(
            estado = DetalleJornadaUiState(
                encontrada = true,
                nombre = "Jorge Salas",
                servicio = "Medicina Interna",
                estadoOperativo = EstadoAsistencia.PRESENTE,
                estadoProgramacion = EstadoProgramacion.PROGRAMADA,
                programacion = listOf(
                    DatoDetalle("Turno", "TM"), DatoDetalle("Inicio", "07:00"), DatoDetalle("Fin", "14:30"),
                    DatoDetalle("Estado de la programación", "Programada"),
                ),
                asistencia = listOf(
                    DatoDetalle("Estado operativo", "Presente"), DatoDetalle("Validación administrativa", "Pendiente"),
                    DatoDetalle("Revisión", "1"),
                ),
                acuse = listOf(
                    DatoDetalle("Capturó", "Tú"), DatoDetalle("Registrado", "07:05:12 (hora del servidor)"),
                    DatoDetalle("Evento", "9007199254741000"),
                ),
            ),
            onRegresar = {},
        )
    }
}
