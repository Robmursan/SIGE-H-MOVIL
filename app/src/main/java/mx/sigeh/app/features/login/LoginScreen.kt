package mx.sigeh.app.features.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.components.BloqueoVersionIncompatible
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTextField
import mx.sigeh.app.core.designsystem.components.StatusPill
import mx.sigeh.app.core.designsystem.components.formatoCuentaRegresiva
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Pantalla 9.1 · Iniciar sesión. */
@Composable
fun LoginScreen(
    estado: LoginUiState,
    sesionVencida: Boolean,
    onIdentificadorChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onIniciarSesion: () -> Unit,
    onReintentarServidor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Encabezado()

        val servidor = estado.servidor
        if (servidor is EstadoServidor.Incompatible) {
            BloqueoVersionIncompatible()
        } else {
            if (sesionVencida) {
                InfoCallout(
                    titulo = "Tu sesión terminó",
                    texto = "Inicia sesión de nuevo para continuar. No se perdió ningún registro confirmado.",
                    tono = TonoEstado.ADVERTENCIA,
                )
            }
            Formulario(estado, onIdentificadorChange, onContrasenaChange, onIniciarSesion)
            InfoCallout(
                texto = "La sesión se guarda cifrada en el dispositivo. Para registrar asistencia se necesita conexión.",
                icono = SigehIcons.Lock,
            )
        }

        PieServidor(servidor, onReintentarServidor)
    }
}

@Composable
private fun Encabezado() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(SigehColors.Marca, SigehShapes.TarjetaPrincipal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SigehIcons.ClipboardCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Text("SIGE-H", style = MaterialTheme.typography.displaySmall, color = SigehColors.Marca, modifier = Modifier.semantics { heading() })
        Text(
            "Pase de lista del personal de enfermería",
            style = MaterialTheme.typography.bodyMedium,
            color = SigehColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Formulario(
    estado: LoginUiState,
    onIdentificadorChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onIniciarSesion: () -> Unit,
) {
    SigehCard(forma = SigehShapes.TarjetaPrincipal, borde = BorderStroke(1.dp, SigehColors.OutlineVariant)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SigehTextField(
                etiqueta = "Identificador",
                valor = estado.identificador,
                onValorChange = onIdentificadorChange,
                marcador = "Tu identificador de SIGE-H",
                habilitado = !estado.cargando,
                longitudMaxima = LONGITUD_MAXIMA_IDENTIFICADOR,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                testTag = "campo_identificador",
            )
            SigehTextField(
                etiqueta = "Contraseña",
                valor = estado.contrasena,
                onValorChange = onContrasenaChange,
                marcador = "••••••••",
                contrasena = true,
                habilitado = !estado.cargando,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIniciarSesion() }),
                testTag = "campo_contrasena",
            )
            if (estado.error != null) {
                InfoCallout(
                    texto = estado.error,
                    tono = if (estado.esperaSegundos > 0) TonoEstado.ADVERTENCIA else TonoEstado.NEGATIVO,
                )
            }
            SigehPrimaryButton(
                texto = if (estado.esperaSegundos > 0) "Espera ${formatoCuentaRegresiva(estado.esperaSegundos)}" else "Iniciar sesión",
                onClick = onIniciarSesion,
                habilitado = estado.puedeEnviar,
                cargando = estado.cargando,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("boton_iniciar_sesion"),
            )
            Text(
                "Si no puedes entrar, acude con Coordinación de enfermería.",
                style = MaterialTheme.typography.bodySmall,
                color = SigehColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PieServidor(servidor: EstadoServidor, onReintentar: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (servidor) {
            EstadoServidor.Demostracion ->
                StatusPill("Modo demostración · datos ficticios", TonoEstado.INFORMATIVO, icono = SigehIcons.Info)
            EstadoServidor.Consultando ->
                StatusPill("Consultando el servidor…", TonoEstado.NEUTRO, icono = SigehIcons.Clock)
            is EstadoServidor.EnLinea ->
                StatusPill("Servidor en línea · contrato API ${servidor.version}", TonoEstado.POSITIVO, icono = SigehIcons.CloudCheck)
            is EstadoServidor.Incompatible ->
                StatusPill("Contrato API ${servidor.version} no compatible", TonoEstado.NEGATIVO, icono = SigehIcons.AlertTriangle)
            EstadoServidor.SinRespuesta -> {
                StatusPill("Servidor sin respuesta", TonoEstado.NEGATIVO, icono = SigehIcons.CloudOff)
                TextButton(onClick = onReintentar) { Text("Reintentar") }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    SigehTheme {
        LoginScreen(
            estado = LoginUiState(servidor = EstadoServidor.EnLinea("1.0.0")),
            sesionVencida = false,
            onIdentificadorChange = {},
            onContrasenaChange = {},
            onIniciarSesion = {},
            onReintentarServidor = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Límite de intentos")
@Composable
private fun LoginScreenLimitePreview() {
    SigehTheme {
        LoginScreen(
            estado = LoginUiState(
                identificador = "cuenta_ficticia",
                contrasena = "x",
                esperaSegundos = 42,
                error = LoginViewModel.textoEspera(42),
            ),
            sesionVencida = true,
            onIdentificadorChange = {},
            onContrasenaChange = {},
            onIniciarSesion = {},
            onReintentarServidor = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Versión incompatible")
@Composable
private fun LoginScreenIncompatiblePreview() {
    SigehTheme {
        LoginScreen(
            estado = LoginUiState(servidor = EstadoServidor.Incompatible("2.0.0")),
            sesionVencida = false,
            onIdentificadorChange = {},
            onContrasenaChange = {},
            onIniciarSesion = {},
            onReintentarServidor = {},
        )
    }
}
