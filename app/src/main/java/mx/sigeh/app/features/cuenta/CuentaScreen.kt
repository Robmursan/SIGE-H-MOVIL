package mx.sigeh.app.features.cuenta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.sigeh.app.core.designsystem.components.AvatarIniciales
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehOutlinedButton
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Accesos a las pantallas de M0 que solo existen con `FUNCIONES_FUTURAS`. */
data class AccesosFuturos(
    val onSincronizacion: () -> Unit = {},
    val onResumenPase: () -> Unit = {},
    val onAgenda: () -> Unit = {},
)

@Composable
fun CuentaRoute(
    onCambiarServicio: () -> Unit,
    onSesionCerrada: () -> Unit,
    accesosFuturos: AccesosFuturos,
    viewModel: CuentaViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val alCerrar by rememberUpdatedState(onSesionCerrada)
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                CuentaEvento.SesionCerrada -> alCerrar()
            }
        }
    }
    CuentaScreen(
        estado = estado,
        onCambiarServicio = onCambiarServicio,
        onRevocar = viewModel::onRevocar,
        onReintentarSesiones = viewModel::cargarSesiones,
        onCerrarSesion = viewModel::onCerrarSesion,
        accesosFuturos = accesosFuturos,
    )
}

/** Pantalla 9.9 · Cuenta. */
@Composable
fun CuentaScreen(
    estado: CuentaUiState,
    onCambiarServicio: () -> Unit,
    onRevocar: (String) -> Unit,
    onReintentarSesiones: () -> Unit,
    onCerrarSesion: () -> Unit,
    accesosFuturos: AccesosFuturos,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SigehTopBar(titulo = "Cuenta")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarIniciales(estado.identificador, tamano = 56.dp)
                Column {
                    Text(estado.identificador, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("cuenta_identificador"))
                    Text("Cuenta local de SIGE-H", style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                }
            }

            SigehCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SeccionTitulo("Servicio actual")
                        Text(estado.servicio.ifBlank { "Sin servicio elegido" }, style = MaterialTheme.typography.titleMedium)
                        if (estado.hospital.isNotBlank()) {
                            Text(estado.hospital, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant)
                        }
                    }
                    TextButton(onClick = onCambiarServicio, modifier = Modifier.testTag("cuenta_cambiar_servicio")) { Text("Cambiar") }
                }
            }

            SigehCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SeccionTitulo("Sesiones")
                    estado.errorSesiones?.let {
                        InfoCallout(texto = it, tono = TonoEstado.NEGATIVO)
                        TextButton(onClick = onReintentarSesiones) { Text("Reintentar") }
                    }
                    if (estado.cargandoSesiones) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = SigehColors.Primary)
                    }
                    estado.sesiones.forEachIndexed { i, sesion ->
                        if (i > 0) HorizontalDivider(color = SigehColors.OutlineVariant)
                        FilaSesionUi(sesion, onRevocar)
                    }
                }
            }

            if (estado.funcionesFuturas) {
                SigehCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeccionTitulo("Funciones futuras (M0, sin backend)")
                        TextButton(onClick = accesosFuturos.onSincronizacion) { Text("Sincronización y conflictos") }
                        TextButton(onClick = accesosFuturos.onResumenPase) { Text("Resumen y envío del pase") }
                        TextButton(onClick = accesosFuturos.onAgenda) { Text("Agenda propia") }
                    }
                }
            }

            SigehPrimaryButton(
                texto = "Cerrar sesión",
                onClick = onCerrarSesion,
                cargando = estado.cerrandoSesion,
                icono = SigehIcons.LogOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cuenta_cerrar_sesion"),
            )
            Text(
                estado.pie,
                style = MaterialTheme.typography.labelMedium,
                color = SigehColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilaSesionUi(sesion: FilaSesion, onRevocar: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (sesion.revocada) 0.6f else 1f)
            .testTag("sesion_${sesion.sessionId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                sesion.actual -> StatusPill("Esta sesión", TonoEstado.POSITIVO, icono = SigehIcons.Check)
                sesion.revocada -> StatusPill("Revocada", TonoEstado.NEUTRO, icono = SigehIcons.Ban)
            }
            Text(sesion.detalle, style = MaterialTheme.typography.bodySmall, color = SigehColors.Tinta)
        }
        if (!sesion.actual && !sesion.revocada) {
            SigehOutlinedButton(
                texto = if (sesion.cerrando) "Cerrando…" else "Cerrar",
                onClick = { onRevocar(sesion.sessionId) },
                habilitado = !sesion.cerrando,
                modifier = Modifier.testTag("sesion_cerrar_${sesion.sessionId}"),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun CuentaPreview() {
    SigehTheme {
        CuentaScreen(
            estado = CuentaUiState(
                identificador = "cuenta_ficticia",
                servicio = "Medicina Interna",
                hospital = "Hospital A",
                cargandoSesiones = false,
                sesiones = listOf(
                    FilaSesion("ses-0001", "Iniciada 20 nov 07:00 · Expira 20 nov 15:00 · inactividad 20 nov 07:30", true, false, false),
                    FilaSesion("ses-0002", "Iniciada 20 nov 06:40 · Expira 20 nov 14:40 · inactividad 20 nov 07:10", false, false, false),
                    FilaSesion("ses-0003", "Iniciada 19 nov 07:05 · Expira 19 nov 15:05 · inactividad 19 nov 07:35", false, true, false),
                ),
                pie = "App 0.2.0-m2 · contrato API 1.0.0",
                funcionesFuturas = true,
            ),
            onCambiarServicio = {},
            onRevocar = {},
            onReintentarSesiones = {},
            onCerrarSesion = {},
            accesosFuturos = AccesosFuturos(),
        )
    }
}
