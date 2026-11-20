package mx.sigeh.app.features.sincronizacion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.components.CargandoPantalla
import mx.sigeh.app.core.designsystem.components.EstadoVacio
import mx.sigeh.app.core.designsystem.components.EtiquetaTipo
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SeccionTitulo
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTopBar
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.designsystem.theme.tabular
import mx.sigeh.app.features.paselista.comandosDeEjemplo

/** Pantalla 10 · Estado de sincronización. */
@Composable
fun SincronizacionScreen(
    estado: SincronizacionUiState,
    onRegresar: () -> Unit,
    onReintentar: () -> Unit,
    onResolver: (String) -> Unit,
    onMensajeMostrado: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(estado.mensaje) {
        val texto = estado.mensaje ?: return@LaunchedEffect
        snackbar.showSnackbar(texto)
        onMensajeMostrado()
    }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            SigehTopBar(titulo = "Sincronización", onRegresar = onRegresar, descripcionRegresar = "Volver")
            if (estado.cargando) {
                CargandoPantalla(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { TarjetaConexion(estado, onReintentar) }
                    if (estado.sesionVencida) {
                        item {
                            InfoCallout(
                                titulo = "Envío detenido",
                                texto = "Tu sesión venció. Inicia sesión de nuevo para reanudar el envío; lo capturado sigue en el teléfono.",
                                tono = TonoEstado.NEGATIVO,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    item { SeccionTitulo("Registros de hoy", Modifier.padding(top = 8.dp)) }
                    if (estado.registros.isEmpty()) {
                        item { EstadoVacio("No hay registros en este teléfono.") }
                    }
                    items(estado.registros, key = { it.clientEventId }) { registro ->
                        TarjetaRegistro(registro, onResolver)
                    }
                    item {
                        Text(
                            "Cada registro conserva su identificador para que un reenvío no lo duplique. " +
                                "Si tu sesión vence, el envío se detiene hasta que vuelvas a iniciar sesión.",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 17.sp),
                            color = SigehColors.OnSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun TarjetaConexion(estado: SincronizacionUiState, onReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SigehColors.InverseSurface, SigehShapes.TarjetaDestacada)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Icon(
                if (estado.enLinea) SigehIcons.CloudCheck else SigehIcons.WifiOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        estado.enLinea -> "En línea"
                        estado.sinConexionDesde != null -> "Sin conexión desde las ${estado.sinConexionDesde}"
                        else -> "Sin conexión"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    estado.ultimoEnvioCorrecto?.let { "Último envío correcto: $it" } ?: "Aún no hay envíos correctos",
                    style = MaterialTheme.typography.bodySmall,
                    color = SigehColors.TextoSobreOscuro,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContadorOscuro(estado.porEnviar, "Por enviar", Modifier.weight(1f))
            ContadorOscuro(estado.conConflicto, "Con conflicto", Modifier.weight(1f))
            ContadorOscuro(estado.aplicados, "Aplicados hoy", Modifier.weight(1f))
        }
        Button(
            onClick = onReintentar,
            shape = SigehShapes.Pildora,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SigehColors.OnPrimaryContainer),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(SigehIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Reintentar ahora", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ContadorOscuro(valor: Int, etiqueta: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SigehColors.SuperficieOscuraElevada, SigehShapes.Campo)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valor.toString(), style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp).tabular(), color = Color.White)
        Text(etiqueta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = SigehColors.TextoSobreOscuro)
    }
}

@Composable
private fun TarjetaRegistro(registro: RegistroSyncUi, onResolver: (String) -> Unit) {
    SigehCard(forma = SigehShapes.Aviso) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EtiquetaTipo(registro.tipo)
                    Text(registro.titulo, style = MaterialTheme.typography.labelLarge)
                }
                StatusPill(registro.estado.texto, registro.estado.tono)
            }
            Text(
                registro.detalle,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = SigehColors.OnSurfaceVariant,
            )
            if (registro.esConflicto) {
                SigehPrimaryButton(
                    texto = "Resolver",
                    onClick = { onResolver(registro.clientEventId) },
                    alto = 40.dp,
                    color = SigehColors.Error,
                )
            }
        }
    }
}

internal fun sincronizacionDeEjemplo(): SincronizacionUiState {
    val comandos = comandosDeEjemplo()
    return SincronizacionUiState(
        cargando = false,
        enLinea = false,
        sinConexionDesde = "07:08",
        ultimoEnvioCorrecto = "07:05",
        porEnviar = comandos.count { it.estado.porEnviar },
        conConflicto = comandos.count { it.estado == mx.sigeh.app.core.model.CommandStatus.CONFLICTO },
        aplicados = comandos.count { it.estado.aplicado },
        registros = ordenarRegistros(comandos).map {
            describirRegistro(
                comando = it,
                enLinea = false,
                aplicadoEnMillis = null,
                servicioAjeno = if (it.servicioId == "svc-cg") "Cirugía General" else null,
            )
        },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SincronizacionScreenPreview() {
    SigehTheme {
        SincronizacionScreen(
            estado = sincronizacionDeEjemplo(),
            onRegresar = {},
            onReintentar = {},
            onResolver = {},
            onMensajeMostrado = {},
        )
    }
}
