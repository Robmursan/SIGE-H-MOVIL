package mx.sigeh.app.core.network.lecturas

import kotlinx.serialization.Serializable
import mx.sigeh.app.core.model.EstadoProgramacion

/*
 * Lecturas opcionales de M2 (prompt §6.4), copiadas del contrato `sigeh-v1` (SHA-256 a6fbe480…; sin cambios desde ab9dd2a4…):
 * SIGE-H-WEB `apps/api/src/modules/lecturas/read.dto.ts` (main 2d1ec62). Dependen de permisos
 * propuestos que normalmente no tiene un jefe de servicio: la app oculta la pestaña sin ellos.
 */

/** Respuesta de `GET /programaciones` (`SchedulePageDto`). */
@Serializable
data class SchedulePageDto(
    val items: List<ScheduleReadDto>,
    val nextCursor: String? = null,
    val limit: Int,
    val order: String,
)

/** Una jornada programada (`ScheduleReadDto`). Trae `empleadoId` pero **no** el nombre. */
@Serializable
data class ScheduleReadDto(
    val programacionId: String,
    val empleadoId: String,
    val servicioId: String,
    val horarioId: String? = null,
    val turnoCodigo: String? = null,
    /** date-time con offset. */
    val inicioProgramado: String,
    /** date-time con offset. */
    val finProgramado: String,
    val estado: EstadoProgramacion,
)

/** Respuesta de `GET /empleados` (`EmployeePageDto`): padrón vigente del servicio, sin RUD. */
@Serializable
data class EmployeePageDto(
    val items: List<EmployeeReadDto>,
    val nextCursor: String? = null,
    val limit: Int,
    val order: String,
)

@Serializable
data class EmployeeReadDto(
    val empleadoId: String,
    val nombres: String,
    val primerApellido: String,
    val activo: Boolean,
)
