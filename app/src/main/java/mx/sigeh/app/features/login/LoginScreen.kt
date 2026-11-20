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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.components.InfoCallout
import mx.sigeh.app.core.designsystem.components.SigehCard
import mx.sigeh.app.core.designsystem.components.SigehPrimaryButton
import mx.sigeh.app.core.designsystem.components.SigehTextField
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.TonoEstado

/** Pantalla 1 · Inicio de sesión. */
@Composable
fun LoginScreen(
    estado: LoginUiState,
    sesionVencida: Boolean,
    onUsuarioChange: (String) -> Unit,
    onContrasenaChange: (String) -> Unit,
    onIniciarSesion: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(SigehColors.Primary, SigehShapes.TarjetaPrincipal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SigehIcons.ClipboardCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Text(
                "SIGE-H",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Gestión del personal de enfermería",
                style = MaterialTheme.typography.bodyMedium,
                color = SigehColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (sesionVencida) {
            InfoCallout(
                titulo = "Tu sesión venció",
                texto = "Inicia sesión de nuevo para seguir enviando lo que quedó guardado en este teléfono.",
                tono = TonoEstado.ADVERTENCIA,
            )
        }

        SigehCard(forma = SigehShapes.TarjetaPrincipal, borde = BorderStroke(1.dp, SigehColors.OutlineVariant)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SigehTextField(
                    etiqueta = "Usuario",
                    valor = estado.usuario,
                    onValorChange = onUsuarioChange,
                    marcador = "Tu usuario institucional",
                    habilitado = !estado.cargando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    testTag = "campo_usuario",
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
                    InfoCallout(texto = estado.error, tono = TonoEstado.NEGATIVO)
                }
                SigehPrimaryButton(
                    texto = "Iniciar sesión",
                    onClick = onIniciarSesion,
                    habilitado = estado.puedeEnviar,
                    cargando = estado.cargando,
                    modifier = Modifier.fillMaxWidth(),
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

        InfoCallout(
            texto = "El inicio de sesión requiere conexión. La sesión se guarda cifrada en este teléfono " +
                "y se cierra al vencer tus permisos.",
            icono = SigehIcons.Lock,
        )

        Text(
            estado.pieServidor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
            color = SigehColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    SigehTheme {
        LoginScreen(
            estado = LoginUiState(),
            sesionVencida = false,
            onUsuarioChange = {},
            onContrasenaChange = {},
            onIniciarSesion = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Sesión vencida")
@Composable
private fun LoginScreenVencidaPreview() {
    SigehTheme {
        LoginScreen(
            estado = LoginUiState(usuario = "mhernandez", error = "Usuario o contraseña incorrectos."),
            sesionVencida = true,
            onUsuarioChange = {},
            onContrasenaChange = {},
            onIniciarSesion = {},
        )
    }
}
