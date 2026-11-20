package mx.sigeh.app.features.sincronizacion

import mx.sigeh.app.core.model.DecisionConflicto
import mx.sigeh.app.core.model.EstadoAsistencia

data class LadoConflicto(
    val estado: EstadoAsistencia,
    val detalle: String,
)

data class ConflictoUiState(
    val cargando: Boolean = true,
    val noDisponible: Boolean = false,
    val subtitulo: String = "",
    val etiquetaPersona: String = "",
    val local: LadoConflicto? = null,
    val servidor: LadoConflicto? = null,
    val servidorValidado: Boolean = false,
    val opcion: DecisionConflicto = DecisionConflicto.CONSERVAR_SERVIDOR,
    val motivo: String = "",
    val enviando: Boolean = false,
) {
    val pideMotivo: Boolean get() = opcion == DecisionConflicto.SOLICITAR_CORRECCION

    val puedeConfirmar: Boolean
        get() = !cargando && !noDisponible && local != null && !enviando && (!pideMotivo || motivo.isNotBlank())
}

sealed interface ConflictoEvento {
    data object Resuelto : ConflictoEvento
}

internal fun conflictoDeEjemplo(opcion: DecisionConflicto = DecisionConflicto.CONSERVAR_SERVIDOR) = ConflictoUiState(
    cargando = false,
    subtitulo = "Paola Gutiérrez L. · Matutino · Sáb 14 nov",
    etiquetaPersona = "P. Gutiérrez",
    local = LadoConflicto(EstadoAsistencia.AUSENTE, "Capturado por ti a las 07:12"),
    servidor = LadoConflicto(EstadoAsistencia.PRESENTE, "Registrado y validado por la supervisora L. Ortega a las 07:20"),
    servidorValidado = true,
    opcion = opcion,
)
