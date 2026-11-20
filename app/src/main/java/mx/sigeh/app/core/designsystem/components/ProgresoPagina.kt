package mx.sigeh.app.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.sigeh.app.core.designsystem.theme.SigehColors
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.core.designsystem.theme.tabular
import mx.sigeh.app.core.model.EstadoAsistencia

/** «3 de 5 capturables registradas · en esta página». Nunca se presenta como total del servicio. */
fun textoProgresoPagina(capturadas: Int, capturables: Int): String =
    "$capturadas de $capturables capturables registradas · en esta página"

/** Orden fijo de los chips de conteo. */
private val ORDEN_CONTEOS = listOf(
    EstadoAsistencia.PRESENTE,
    EstadoAsistencia.AUSENTE,
    EstadoAsistencia.PARCIAL,
    EstadoAsistencia.PENDIENTE,
)

/**
 * Avance del pase **en la página cargada** (prompt M2 §9.3.3). Quien llama calcula los números:
 * [capturables] cuenta los items con `capturable` o `capturada`; [conteos] es el conteo por estado
 * operativo de esos items (`PENDIENTE` se muestra como «Por capturar»). Los estados en cero no se muestran.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgresoPagina(
    capturadas: Int,
    capturables: Int,
    conteos: Map<EstadoAsistencia, Int>,
    modifier: Modifier = Modifier,
) {
    val texto = textoProgresoPagina(capturadas, capturables)
    val fraccion = if (capturables > 0) (capturadas.toFloat() / capturables).coerceIn(0f, 1f) else 0f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("progreso_pagina"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.labelLarge.tabular(), color = SigehColors.Tinta)
        LinearProgressIndicator(
            progress = { fraccion },
            color = SigehColors.Acento,
            trackColor = SigehColors.OutlineVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .semantics {
                    contentDescription = texto
                    progressBarRangeInfo = ProgressBarRangeInfo(capturadas.toFloat(), 0f..capturables.coerceAtLeast(1).toFloat())
                },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ORDEN_CONTEOS.forEach { estado ->
                val cantidad = conteos[estado] ?: 0
                if (cantidad > 0) {
                    val apariencia = estado.apariencia(capturable = true)
                    BadgeEstado(
                        apariencia = apariencia,
                        texto = "${apariencia.texto} · $cantidad",
                        modifier = Modifier.testTag("conteo_${estado.name}"),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ProgresoPaginaPreview() {
    SigehTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ProgresoPagina(
                capturadas = 3,
                capturables = 5,
                conteos = mapOf(
                    EstadoAsistencia.PRESENTE to 1,
                    EstadoAsistencia.AUSENTE to 1,
                    EstadoAsistencia.PARCIAL to 1,
                    EstadoAsistencia.PENDIENTE to 2,
                ),
            )
            ProgresoPagina(capturadas = 0, capturables = 0, conteos = emptyMap())
        }
    }
}
