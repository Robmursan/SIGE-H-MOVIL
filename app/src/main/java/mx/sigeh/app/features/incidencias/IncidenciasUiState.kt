package mx.sigeh.app.features.incidencias

import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.features.paselista.comandosDeEjemplo
import mx.sigeh.app.features.paselista.paseDeEjemplo
import mx.sigeh.app.features.sincronizacion.RegistroSyncUi
import mx.sigeh.app.features.sincronizacion.describirRegistro

data class JornadaParaIncidencia(
    val programacionId: String,
    val nombre: String,
    val detalle: String,
)

/**
 * Pestaña "Incidencias" (pantalla adicional a los 11 mockups, acordada con la persona usuaria):
 * lista lo registrado hoy en el teléfono y el personal exigible para registrar una incidencia.
 */
data class IncidenciasUiState(
    val cargando: Boolean = true,
    val sinProgramacion: Boolean = false,
    val subtitulo: String = "",
    val porEnviar: Int = 0,
    val registradas: List<RegistroSyncUi> = emptyList(),
    val jornadas: List<JornadaParaIncidencia> = emptyList(),
    val puedeRegistrar: Boolean = true,
)

internal fun incidenciasDeEjemplo(): IncidenciasUiState {
    val comandos = comandosDeEjemplo()
    return IncidenciasUiState(
        cargando = false,
        subtitulo = "Medicina Interna · Matutino · Sáb 14 nov",
        porEnviar = comandos.count { it.estado.porEnviar },
        registradas = comandos
            .filter { it.tipo == CommandType.INCIDENCIA_PRELIMINAR }
            .map { describirRegistro(it, enLinea = false, aplicadoEnMillis = null, servicioAjeno = null) },
        jornadas = paseDeEjemplo().jornadas.filter { it.exigible }.map {
            JornadaParaIncidencia(it.programacionId, it.nombre, "${it.categoria} · RUD ···${it.rudUltimos4}")
        },
    )
}
