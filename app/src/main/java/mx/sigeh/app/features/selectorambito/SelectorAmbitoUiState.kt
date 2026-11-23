package mx.sigeh.app.features.selectorambito

import mx.sigeh.app.core.model.RolAlcance

/**
 * Una tarjeta del selector: un ámbito propio con permiso de asistencia (prompt M2 §9.2), de
 * `/auth/me/contextos` o, como respaldo, de `/auth/me`.
 */
data class OpcionAmbito(
    /** `hospitalId|divisionId|servicioId`, estable entre recomposiciones. */
    val clave: String,
    val hospital: String,
    val division: String?,
    /** Null en un ámbito de hospital o división: la tarjeta va deshabilitada. */
    val servicio: String?,
    val rol: RolAlcance?,
    /** Chip «Capturar» (`ASISTENCIA_CAPTURAR`). */
    val capturar: Boolean,
    /** Chip «Consultar» (`ASISTENCIA_VALIDAR`). */
    val consultar: Boolean,
    val habilitada: Boolean,
    val seleccionada: Boolean,
    /** «Vigente hasta 1 dic 00:00» en la zona del hospital; null si el servidor no informa término. */
    val vigencia: String? = null,
)

data class SelectorAmbitoUiState(
    val cargando: Boolean = true,
    val opciones: List<OpcionAmbito> = emptyList(),
    val cerrandoSesion: Boolean = false,
) {
    val puedeContinuar: Boolean get() = opciones.any { it.seleccionada && it.habilitada }

    /** La sesión no trae ningún ámbito con `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`. */
    val sinAmbitos: Boolean get() = !cargando && opciones.isEmpty()
}

const val TEXTO_AMBITO_SIN_SERVICIO = "Este ámbito no indica un servicio. Pide a Coordinación una asignación por servicio."

sealed interface SelectorAmbitoEvento {
    data object Continuar : SelectorAmbitoEvento
    data object SesionCerrada : SelectorAmbitoEvento
}
