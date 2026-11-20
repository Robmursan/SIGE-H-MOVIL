package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehShapes

/** Tarjeta blanca con borde #DCE4E5 (16 dp por defecto). */
@Composable
fun SigehCard(
    modifier: Modifier = Modifier,
    forma: Shape = SigehShapes.Tarjeta,
    borde: BorderStroke = BorderStroke(1.dp, SigehColors.OutlineVariant),
    color: Color = Color.White,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = forma, color = color, border = borde) {
        Column(content = contenido)
    }
}

/** Título de sección (14 sp, 600, #24363A). */
@Composable
fun SeccionTitulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.titleSmall,
        color = SigehColors.Tinta,
        modifier = modifier.semantics { heading() },
    )
}

/** Etiqueta de tipo en mayúsculas ("CONVENIO", "ASISTENCIA"). */
@Composable
fun EtiquetaTipo(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
        color = SigehColors.OnSurfaceVariant,
        modifier = modifier,
    )
}

/** Fila "dato: valor" para listas de definición. */
@Composable
fun FilaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = SigehColors.OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.titleSmall, color = SigehColors.OnSurface, textAlign = TextAlign.End)
    }
}

/** Barra inferior de acciones (blanca, con borde superior) para pantallas de detalle. */
@Composable
fun BarraAcciones(modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = SigehColors.OutlineVariant)
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = contenido,
            )
        }
    }
}

@Composable
fun CargandoPantalla(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SigehColors.Primary)
    }
}

@Composable
fun EstadoVacio(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = SigehColors.OnSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    )
}
