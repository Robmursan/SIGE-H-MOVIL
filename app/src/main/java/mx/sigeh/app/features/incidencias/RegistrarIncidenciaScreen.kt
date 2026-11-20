package mx.sigeh.app.features.incidencias

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.components.BarraAcciones
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.OptionCard
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehSegmentedButtons
import mx.sigeh.app.core.designsystem.components.SigehTextField
import mx.sigeh.app.core.designsystem.components.SigehTimeField
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Pantalla 5 · Registrar incidencia (preliminar). */
@Composable
fun RegistrarIncidenciaScreen(
    estado: RegistrarIncidenciaUiState,
    onCancelar: () -> Unit,
    onTipo: (String) -> Unit,
    onJornadaCompleta: (Boolean) -> Unit,
    onDesde: (String) -> Unit,
    onHasta: (String) -> Unit,
    onObservacion: (String) -> Unit,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SigehTopBar(titulo = "Registrar incidencia", onRegresar = onCancelar, descripcionRegresar = "Volver")
        val jornada = estado.jornada
        when {
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            estado.noEncontrada || jornada == null -> Column(Modifier.weight(1f)) {
                EstadoVacio("No se encontró la jornada en el pase de hoy.")
            }
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SigehCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(jornada.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${jornada.categoria} · RUD ···${jornada.rudUltimos4}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SigehColors.OnSurfaceVariant,
                        )
                        HorizontalDivider(color = SigehColors.SurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(SigehIcons.Calendar, contentDescription = null, tint = SigehColors.Primary, modifier = Modifier.size(18.dp))
                            Text(estado.contextoJornada, style = MaterialTheme.typography.bodySmall, color = SigehColors.Tinta)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeccionTitulo("Tipo de incidencia")
                    estado.tipos.forEach { tipo ->
                        OptionCard(
                            titulo = tipo.nombre,
                            detalle = tipo.grupo,
                            seleccionado = tipo.id == estado.tipoSeleccionadoId,
                            onClick = { onTipo(tipo.id) },
                            habilitado = estado.puedeRegistrar,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SeccionTitulo("Periodo afectado")
                    SigehSegmentedButtons(
                        opciones = listOf("Jornada completa", "Parte de la jornada"),
                        seleccionado = if (estado.jornadaCompleta) 0 else 1,
                        onSeleccionar = { onJornadaCompleta(it == 0) },
                        habilitado = estado.periodoEditable,
                    )
                    if (!estado.periodoEditable) {
                        Text(
                            "Este tipo se registra por jornada completa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SigehColors.OnSurfaceVariant,
                        )
                    }
                    if (!estado.jornadaCompleta) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SigehTimeField("Desde", estado.desde, onDesde, Modifier.weight(1f), etiquetaPequena = true)
                            SigehTimeField("Hasta", estado.hasta, onHasta, Modifier.weight(1f), etiquetaPequena = true)
                        }
                        estado.errorHorario?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = SigehColors.Error)
                        }
                    }
                }

                SigehTextField(
                    etiqueta = "Observación (opcional)",
                    valor = estado.observacion,
                    onValorChange = onObservacion,
                    marcador = "Sin datos clínicos ni diagnósticos",
                    multilinea = true,
                )

                InfoCallout(
                    texto = "Registro preliminar. Supervisión o Recursos Humanos lo clasifican y autorizan desde la web. " +
                        "Desde el teléfono no se autorizan incidencias.",
                    tono = TonoEstado.ADVERTENCIA,
                )
            }
        }
        BarraAcciones {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SigehOutlinedButton("Cancelar", onCancelar, Modifier.weight(1f))
                SigehPrimaryButton(
                    texto = "Guardar",
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

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun RegistrarIncidenciaScreenPreview() {
    SigehTheme {
        RegistrarIncidenciaScreen(
            estado = registrarIncidenciaDeEjemplo(),
            onCancelar = {},
            onTipo = {},
            onJornadaCompleta = {},
            onDesde = {},
            onHasta = {},
            onObservacion = {},
            onGuardar = {},
        )
    }
}
