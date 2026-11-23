package mx.sigeh.app.features.selectorambito

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.components.BarraAcciones
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.RadioIndicador
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.RolAlcance

/** Pantalla 9.2 · Elige tu servicio. */
@Composable
fun SelectorAmbitoScreen(
    estado: SelectorAmbitoUiState,
    alcanceVencido: Boolean,
    onSeleccionar: (String) -> Unit,
    onContinuar: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize(),
    ) {
        SigehTopBar(
            titulo = "Elige tu servicio",
            subtitulo = "Ámbitos autorizados para esta sesión",
            acciones = {
                IconButton(onClick = onCerrarSesion, enabled = !estado.cerrandoSesion, modifier = Modifier.testTag("selector_cerrar_sesion")) {
                    Icon(SigehIcons.LogOut, contentDescription = "Cerrar sesión", tint = SigehColors.SobreMarca)
                }
            },
        )
        when {
            estado.cargando -> CargandoPantalla(Modifier.weight(1f))
            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .selectableGroup(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (alcanceVencido) {
                    item {
                        InfoCallout(
                            titulo = "Tu ámbito anterior venció",
                            texto = "Elige otro de tus servicios autorizados.",
                            tono = TonoEstado.ADVERTENCIA,
                        )
                    }
                }
                if (estado.sinAmbitos) {
                    item {
                        InfoCallout(
                            titulo = "Sin servicios para el pase de lista",
                            texto = "Tu sesión no tiene ámbitos con permiso para capturar o consultar asistencia. " +
                                "Pide a Coordinación de enfermería una asignación por servicio.",
                            tono = TonoEstado.ADVERTENCIA,
                        )
                    }
                }
                items(estado.opciones, key = { it.clave }) { opcion ->
                    TarjetaAmbito(opcion, onSeleccionar = { onSeleccionar(opcion.clave) })
                }
            }
        }
        BarraAcciones {
            SigehPrimaryButton(
                texto = "Continuar al pase de lista",
                onClick = onContinuar,
                habilitado = estado.puedeContinuar,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selector_continuar"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaAmbito(opcion: OpcionAmbito, onSeleccionar: () -> Unit) {
    Surface(
        selected = opcion.seleccionada,
        onClick = onSeleccionar,
        enabled = opcion.habilitada,
        shape = SigehShapes.Tarjeta,
        color = if (opcion.seleccionada) SigehColors.SeleccionSuave else Color.White,
        border = BorderStroke(2.dp, if (opcion.seleccionada) SigehColors.Primary else SigehColors.OutlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.RadioButton }
            .testTag("ambito_${opcion.clave}"),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (opcion.habilitada) 1f else 0.72f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    listOfNotNull(opcion.hospital, opcion.division).joinToString(" › "),
                    style = MaterialTheme.typography.labelMedium,
                    color = SigehColors.OnSurfaceVariant,
                )
                Text(
                    opcion.servicio ?: "Sin servicio indicado",
                    style = MaterialTheme.typography.titleMedium,
                    color = SigehColors.OnSurface,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (opcion.rol) {
                        RolAlcance.TITULAR -> StatusPill("Titular", TonoEstado.NEUTRO, icono = SigehIcons.UserCheck)
                        RolAlcance.ENCARGADO -> StatusPill("Encargado", TonoEstado.NEUTRO, icono = SigehIcons.User)
                        null -> Unit
                    }
                    if (opcion.capturar) StatusPill("Capturar", TonoEstado.POSITIVO, icono = SigehIcons.ClipboardCheck)
                    if (opcion.consultar) StatusPill("Consultar", TonoEstado.INFORMATIVO, icono = SigehIcons.Eye)
                }
                opcion.vigencia?.let { vigencia ->
                    Text(
                        vigencia,
                        style = MaterialTheme.typography.bodySmall,
                        color = SigehColors.OnSurfaceVariant,
                    )
                }
                if (!opcion.habilitada) {
                    InfoCallout(texto = TEXTO_AMBITO_SIN_SERVICIO, tono = TonoEstado.NEUTRO, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (opcion.habilitada) RadioIndicador(opcion.seleccionada, Modifier.padding(top = 2.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SelectorAmbitoScreenPreview() {
    SigehTheme {
        SelectorAmbitoScreen(
            estado = SelectorAmbitoUiState(
                cargando = false,
                opciones = listOf(
                    OpcionAmbito("a", "Hospital A", "División de Medicina", "Medicina Interna", RolAlcance.TITULAR, true, true, true, true),
                    OpcionAmbito("b", "Hospital A", "División de Cirugía", "Cirugía General", RolAlcance.ENCARGADO, false, true, true, false, vigencia = "Vigente hasta 1 dic 00:00"),
                    OpcionAmbito("c", "Hospital A", "División de Medicina", null, RolAlcance.TITULAR, false, true, false, false),
                ),
            ),
            alcanceVencido = false,
            onSeleccionar = {},
            onContinuar = {},
            onCerrarSesion = {},
        )
    }
}
