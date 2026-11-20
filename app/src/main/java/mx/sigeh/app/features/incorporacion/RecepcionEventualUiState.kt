package mx.sigeh.app.features.incorporacion

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.AsignacionOperativa

data class RecepcionEventualUiState(
    val cargando: Boolean = true,
    val noEncontrada: Boolean = false,
    val subtitulo: String = "",
    val asignacion: AsignacionOperativa? = null,
    val inicioEfectivo: String = "",
    val enLinea: Boolean = false,
    val tieneFacultad: Boolean = true,
    val enviando: Boolean = false,
    val mostrarRechazo: Boolean = false,
    val motivoRechazo: String = "",
    val error: String? = null,
) {
    val yaIncorporada: Boolean get() = asignacion?.incorporadaA != null

    /** Recibir eventuales requiere conexión y facultad; sin ellas los botones no quedan activos. */
    val accionesHabilitadas: Boolean
        get() = asignacion != null && !yaIncorporada && enLinea && tieneFacultad && !enviando

    val motivoBloqueo: String?
        get() = when {
            yaIncorporada -> null
            !tieneFacultad -> "Como encargada de turno no tienes facultad para recibir personal eventual. Pide a la jefatura titular que lo haga."
            !enLinea -> "Sin conexión no puedes confirmar ni rechazar: la cobertura solo cuenta cuando el servidor registra la incorporación."
            else -> null
        }
}

sealed interface RecepcionEvento {
    data object Terminada : RecepcionEvento
}

internal fun recepcionDeEjemplo(enLinea: Boolean = true) = RecepcionEventualUiState(
    cargando = false,
    subtitulo = "Medicina Interna · Matutino",
    asignacion = DatosFicticios.asignacionSofia,
    inicioEfectivo = "07:55",
    enLinea = enLinea,
)
