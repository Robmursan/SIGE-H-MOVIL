package mx.sigeh.app.features.paselista

import mx.sigeh.app.core.common.FormatoHora
import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.ComandoLocal
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoNecesidadCobertura
import mx.sigeh.app.core.model.EstadoPaseLista
import mx.sigeh.app.core.model.NecesidadCobertura
import mx.sigeh.app.core.model.PaseLista

enum class EstadoPaso { COMPLETO, EN_CURSO, PENDIENTE }

data class PasoEnvio(val titulo: String, val detalle: String, val estado: EstadoPaso)

data class ConteoResumen(val valor: Int, val etiqueta: String, val tono: TonoEstado?)

data class ConflictoPorAtender(val clientEventId: String, val nombre: String)

data class FilaCobertura(
    val necesidadId: String,
    val titulo: String,
    val detalle: String,
    val estadoTexto: String,
    val tono: TonoEstado,
    val asignacionId: String?,
)

data class ResumenPaseUiState(
    val cargando: Boolean = true,
    val subtitulo: String = "",
    val pasos: List<PasoEnvio> = emptyList(),
    val conteos: List<ConteoResumen> = emptyList(),
    val conflictos: List<ConflictoPorAtender> = emptyList(),
    val sinCapturar: Int = 0,
    val cobertura: List<FilaCobertura> = emptyList(),
)

/** Resumen calculado con las mismas filas que el pase (prompt §6.6). */
fun construirResumen(
    subtitulo: String,
    pase: PaseLista,
    comandos: List<ComandoLocal>,
    necesidades: List<NecesidadCobertura>,
    enLinea: Boolean,
): ResumenPaseUiState {
    val filas = construirFilas(pase, comandos)
    val contadores = calcularContadores(filas)
    val exigibles = filas.filter { it.exigible }
    val idsPase = pase.jornadas.map { it.programacionId }.toSet()
    val comandosPase = comandos.filter { it.programacionId in idsPase }
    val sinEnviar = comandosPase.count {
        it.estado == CommandStatus.PENDIENTE_LOCAL || it.estado == CommandStatus.ENVIANDO ||
            it.estado == CommandStatus.ERROR_REINTENTABLE
    }
    val horaCaptura = pase.envioSolicitadoMillis ?: comandosPase.maxOfOrNull { it.creadoEnMillis }

    val pasos = listOf(
        PasoEnvio(
            titulo = "Capturado en el teléfono",
            detalle = listOfNotNull(
                horaCaptura?.let(FormatoHora::hora),
                "${contadores.capturados} de ${contadores.exigibles} exigibles",
            ).joinToString(" · "),
            estado = if (contadores.capturados > 0) EstadoPaso.COMPLETO else EstadoPaso.PENDIENTE,
        ),
        when {
            sinEnviar > 0 -> PasoEnvio(
                "Recibido por el servidor",
                if (enLinea) "Enviando…" else "En espera de conexión",
                EstadoPaso.EN_CURSO,
            )
            contadores.capturados > 0 -> PasoEnvio("Recibido por el servidor", "Recibido", EstadoPaso.COMPLETO)
            else -> PasoEnvio("Recibido por el servidor", "Sin capturas", EstadoPaso.PENDIENTE)
        },
        if (pase.estado == EstadoPaseLista.VALIDADO) {
            PasoEnvio("Validado por Supervisión", "Validado", EstadoPaso.COMPLETO)
        } else {
            PasoEnvio("Validado por Supervisión", "Pendiente", EstadoPaso.PENDIENTE)
        },
    )

    val conteos = listOf(
        ConteoResumen(contadores.programados, "Programados", null),
        ConteoResumen(contadores.exigibles, "Exigibles", null),
        ConteoResumen(exigibles.count { it.estado == EstadoAsistencia.PRESENTE }, "Presentes", TonoEstado.POSITIVO),
        ConteoResumen(exigibles.count { it.estado == EstadoAsistencia.AUSENTE }, "Ausentes", TonoEstado.NEGATIVO),
        ConteoResumen(exigibles.count { it.estado == EstadoAsistencia.PARCIAL }, "Parciales", null),
        ConteoResumen(contadores.pendientes, "Sin capturar", TonoEstado.ADVERTENCIA),
    )

    val conflictos = filas
        .filter { it.indicador == IndicadorFila.CONFLICTO && it.clientEventId != null }
        .map { ConflictoPorAtender(it.clientEventId!!, it.nombre) }

    return ResumenPaseUiState(
        cargando = false,
        subtitulo = subtitulo,
        pasos = pasos,
        conteos = conteos,
        conflictos = conflictos,
        sinCapturar = contadores.pendientes,
        cobertura = necesidades.map { it.aFila() },
    )
}

private fun NecesidadCobertura.aFila(): FilaCobertura {
    val (texto, tono) = when (estado) {
        EstadoNecesidadCobertura.IDENTIFICADA -> "Identificada" to TonoEstado.NEUTRO
        EstadoNecesidadCobertura.VALIDADA -> "Validada" to TonoEstado.INFORMATIVO
        EstadoNecesidadCobertura.EN_ASIGNACION -> "En asignación" to TonoEstado.ADVERTENCIA
        EstadoNecesidadCobertura.PARCIAL -> "Parcial" to TonoEstado.ADVERTENCIA
        EstadoNecesidadCobertura.CUBIERTA -> "Cubierta" to TonoEstado.POSITIVO
        EstadoNecesidadCobertura.NO_CUBIERTA -> "No cubierta" to TonoEstado.NEGATIVO
        EstadoNecesidadCobertura.CANCELADA -> "Cancelada" to TonoEstado.NEUTRO
    }
    return FilaCobertura(id, titulo, detalle, texto, tono, asignacionPendienteId)
}

internal fun resumenDeEjemplo(): ResumenPaseUiState = construirResumen(
    subtitulo = "Medicina Interna · Matutino · Sáb 14 nov",
    pase = paseDeEjemplo().copy(envioSolicitadoMillis = mx.sigeh.app.core.common.fechaHoraLocal(2026, 11, 14, 7, 24)),
    comandos = comandosDeEjemplo(),
    necesidades = DatosFicticios.necesidades,
    enLinea = false,
)
