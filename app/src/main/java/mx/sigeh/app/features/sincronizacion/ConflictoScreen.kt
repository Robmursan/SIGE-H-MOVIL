package mx.sigeh.app.features.sincronizacion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.BarraAcciones
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.EtiquetaTipo
import mx.sigeh.app.core.designsystem.components.OptionCard
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTextField
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.DecisionConflicto
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.etiqueta

/** Pantalla 11 · Resolver conflicto. */
@Composable
fun ConflictoScreen(
    estado: ConflictoUiState,
    onRegresar: () -> Unit,
    onOpcion: (DecisionConflicto) -> Unit,
    onMotivoChange: (String) -> Unit,
    onConfirmar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SigehTopBar(
            titulo = "Resolver conflicto",
            subtitulo = estado.subtitulo.takeIf { it.isNotBlank() },
            onRegresar = onRegresar,
            descripcionRegresar = "Volver",
        )
        when {
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            estado.noDisponible -> Column(Modifier.weight(1f)) {
                EstadoVacio("Este conflicto ya se resolvió o ya no está en el teléfono.")
            }
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    estado.local?.let { lado ->
                        TarjetaLado(
                            titulo = "EN ESTE TELÉFONO",
                            lado = lado,
                            pill = { StatusPill("Sin enviar", TonoEstado.ADVERTENCIA) },
                            destacada = false,
                        )
                    }
                    estado.servidor?.let { lado ->
                        TarjetaLado(
                            titulo = "EN EL SERVIDOR",
                            lado = lado,
                            pill = {
                                if (estado.servidorValidado) {
                                    StatusPill("Validado", TonoEstado.POSITIVO, icono = SigehIcons.Lock)
                                } else {
                                    StatusPill("Registrado", TonoEstado.NEUTRO)
                                }
                            },
                            destacada = true,
                        )
                    }
                }
                Text(
                    "La app nunca reemplaza un registro validado con una captura tardía. Elige cómo continuar:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = SigehColors.Tinta,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionCard(
                        titulo = "Conservar lo del servidor",
                        detalle = "Tu captura local se descarta y queda constancia del intento.",
                        seleccionado = estado.opcion == DecisionConflicto.CONSERVAR_SERVIDOR,
                        onClick = { onOpcion(DecisionConflicto.CONSERVAR_SERVIDOR) },
                        radioAlInicio = true,
                    )
                    OptionCard(
                        titulo = "Solicitar corrección a Supervisión",
                        detalle = "Se envía tu captura como solicitud con motivo. Supervisión decide.",
                        seleccionado = estado.opcion == DecisionConflicto.SOLICITAR_CORRECCION,
                        onClick = { onOpcion(DecisionConflicto.SOLICITAR_CORRECCION) },
                        radioAlInicio = true,
                    )
                    if (estado.pideMotivo) {
                        SigehTextField(
                            etiqueta = "Motivo de la corrección",
                            valor = estado.motivo,
                            onValorChange = onMotivoChange,
                            marcador = "Explica qué observaste en el pase",
                            multilinea = true,
                        )
                    }
                }
            }
        }
        BarraAcciones {
            SigehPrimaryButton(
                texto = "Confirmar",
                onClick = onConfirmar,
                habilitado = estado.puedeConfirmar,
                cargando = estado.enviando,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Queda registrado en la auditoría del pase.",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = SigehColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TarjetaLado(
    titulo: String,
    lado: LadoConflicto,
    pill: @Composable () -> Unit,
    destacada: Boolean,
) {
    val colorEstado = when (lado.estado) {
        EstadoAsistencia.PRESENTE -> SigehColors.Primary
        EstadoAsistencia.AUSENTE -> SigehColors.Error
        else -> SigehColors.OnSurface
    }
    SigehCard(
        borde = BorderStroke(if (destacada) 2.dp else 1.dp, if (destacada) SigehColors.Primary else SigehColors.OutlineVariant),
        color = Color.White,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EtiquetaTipo(titulo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lado.estado.etiqueta(),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = colorEstado,
                    modifier = Modifier.weight(1f),
                )
                pill()
            }
            Text(lado.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ConflictoScreenPreview() {
    SigehTheme {
        ConflictoScreen(conflictoDeEjemplo(), onRegresar = {}, onOpcion = {}, onMotivoChange = {}, onConfirmar = {})
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Solicitar corrección")
@Composable
private fun ConflictoCorreccionPreview() {
    SigehTheme {
        ConflictoScreen(
            conflictoDeEjemplo(DecisionConflicto.SOLICITAR_CORRECCION),
            onRegresar = {},
            onOpcion = {},
            onMotivoChange = {},
            onConfirmar = {},
        )
    }
}
