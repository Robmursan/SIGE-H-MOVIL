package mx.sigeh.app.core.model

/** Estados de una necesidad de cobertura (spec §6.2). */
enum class EstadoNecesidadCobertura { IDENTIFICADA, VALIDADA, EN_ASIGNACION, PARCIAL, CUBIERTA, NO_CUBIERTA, CANCELADA }

/** Estados de la disponibilidad de personal eventual (spec §6.2). */
enum class EstadoDisponibilidadEventual { DECLARADA, PRESENTE_VALIDADA, PREASIGNADA, ASIGNADA, INCORPORADA, LIBERADA }

/** Necesidad de cobertura del servicio (tabla `necesidades_cobertura`). En el teléfono es de solo lectura. */
data class NecesidadCobertura(
    val id: String,
    val titulo: String,
    val detalle: String,
    val horario: String,
    val estado: EstadoNecesidadCobertura,
    val asignacionPendienteId: String? = null,
)

data class PersonaEventual(
    val nombre: String,
    val iniciales: String,
    val categoria: String,
    val rudUltimos4: String,
    val competenciasVerificadas: List<String>,
)

/**
 * Asignación operativa (tabla `asignaciones_operativas`, CU-COB-07/08). Asignado no es
 * incorporado e incorporado no es cubierto (RN-COB-02).
 */
data class AsignacionOperativa(
    val id: String,
    val necesidad: NecesidadCobertura,
    val persona: PersonaEventual,
    val llegadaCoordinacion: String,
    val asignadaA: String,
    val estadoEventual: EstadoDisponibilidadEventual,
    val incorporadaA: String? = null,
)
