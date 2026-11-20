package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.designsystem.icon.SigehIcons
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes
import mx.sigeh.app.core.designsystem.theme.tabular

@Composable
private fun EtiquetaCampo(texto: String, pequena: Boolean = false) {
    Text(
        texto,
        style = if (pequena) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = SigehColors.Tinta,
    )
}

/**
 * Campo de texto con etiqueta arriba (como en los mockups), bordes de 12 dp.
 * Con [contrasena] lleva un botón para mostrarla u ocultarla; [longitudMaxima] corta lo que exceda.
 */
@Composable
fun SigehTextField(
    etiqueta: String,
    valor: String,
    onValorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    marcador: String? = null,
    contrasena: Boolean = false,
    multilinea: Boolean = false,
    habilitado: Boolean = true,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    testTag: String? = null,
    longitudMaxima: Int? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EtiquetaCampo(etiqueta)
        OutlinedTextField(
            value = valor,
            onValueChange = { nuevo -> onValorChange(if (longitudMaxima != null) nuevo.take(longitudMaxima) else nuevo) },
            enabled = habilitado,
            singleLine = !multilinea,
            minLines = if (multilinea) 3 else 1,
            isError = error != null,
            placeholder = marcador?.let { { Text(it, color = SigehColors.Marcador) } },
            trailingIcon = if (contrasena) {
                {
                    IconButton(
                        onClick = { visible = !visible },
                        modifier = Modifier.then(if (testTag != null) Modifier.testTag("${testTag}_mostrar") else Modifier),
                    ) {
                        Icon(
                            if (visible) SigehIcons.EyeOff else SigehIcons.Eye,
                            contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = SigehColors.OnSurfaceVariant,
                        )
                    }
                }
            } else {
                null
            },
            visualTransformation = if (contrasena && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = SigehShapes.Campo,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SigehColors.Outline,
                focusedBorderColor = SigehColors.Primary,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                disabledContainerColor = SigehColors.Background,
            ),
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (multilinea) 96.dp else 52.dp)
                .semantics { contentDescription = etiqueta }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        )
    }
}

/** Campo de hora (HH:mm, 24 h). Abre un diálogo con la entrada de hora de Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigehTimeField(
    etiqueta: String,
    hora: String,
    onHoraChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    etiquetaPequena: Boolean = false,
    grande: Boolean = false,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (etiquetaPequena) 6.dp else 8.dp)) {
        EtiquetaCampo(etiqueta, pequena = etiquetaPequena)
        Surface(
            onClick = { abierto = true },
            enabled = habilitado,
            shape = SigehShapes.Campo,
            color = if (habilitado) Color.White else SigehColors.Background,
            border = BorderStroke(1.dp, SigehColors.Outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (grande) 52.dp else 48.dp)
                .semantics { contentDescription = "$etiqueta: $hora. Cambiar hora" },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hora,
                    style = TextStyle(fontSize = if (grande) 18.sp else 16.sp).tabular(),
                    color = SigehColors.OnSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(SigehIcons.Clock, contentDescription = null, tint = SigehColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
    if (abierto) {
        val (h, m) = FormatoHora.separar(hora) ?: (7 to 0)
        val estado = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(etiqueta) },
            text = { TimeInput(state = estado) },
            confirmButton = {
                TextButton(onClick = {
                    onHoraChange(FormatoHora.horaDe(estado.hour, estado.minute))
                    abierto = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } },
        )
    }
}

/** Selector desplegable con el mismo aspecto que los campos. */
@Composable
fun <T> SigehSelectField(
    etiqueta: String,
    opciones: List<T>,
    seleccion: T?,
    textoDe: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
) {
    var expandido by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EtiquetaCampo(etiqueta)
        Box {
            Surface(
                onClick = { expandido = true },
                shape = SigehShapes.Campo,
                color = Color.White,
                border = BorderStroke(1.dp, SigehColors.Outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "$etiqueta: ${seleccion?.let(textoDe) ?: "sin elegir"}" },
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seleccion?.let(textoDe) ?: "Selecciona",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (seleccion == null) SigehColors.Marcador else SigehColors.OnSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(SigehIcons.ChevronDown, contentDescription = null, tint = SigehColors.OnSurfaceVariant)
                }
            }
            DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                opciones.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(textoDe(opcion)) },
                        onClick = {
                            onSeleccion(opcion)
                            expandido = false
                        },
                    )
                }
            }
        }
        if (ayuda != null) {
            Text(ayuda, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = SigehColors.OnSurfaceVariant)
        }
    }
}
