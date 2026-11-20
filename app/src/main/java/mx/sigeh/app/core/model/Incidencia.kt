package mx.sigeh.app.core.model

/**
 * Tipo de incidencia. El catálogo es extensible por hospital (spec §6.1 y CU-INC-01):
 * por eso es un dato que entrega el repositorio y no un enum fijo.
 */
data class TipoIncidencia(
    val id: String,
    val nombre: String,
    val grupo: String,
    val permiteParcial: Boolean,
)

/** Estados de una incidencia (spec §6.2). Desde el teléfono solo se crea PRELIMINAR. */
enum class EstadoIncidencia { PRELIMINAR, EN_REVISION, AUTORIZADA, RECHAZADA, RECLASIFICADA, CANCELADA }
