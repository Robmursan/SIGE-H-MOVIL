package mx.sigeh.app.core.model

/**
 * Jornada de la programación semanal del servicio (`ScheduleReadDto`, M2 §9.8). Muestra lo
 * programado, no la asistencia ni la cobertura real.
 *
 * @property nombre solo si se pudo cruzar con `GET /empleados` (permiso `PERSONAL_CONSULTAR`).
 */
data class JornadaSemanal(
    val programacionId: String,
    val empleadoId: String,
    val nombre: String?,
    val horarioId: String?,
    val turnoCodigo: String?,
    /** ISO-8601 con offset. */
    val inicioProgramado: String,
    /** ISO-8601 con offset. */
    val finProgramado: String,
    val estado: EstadoProgramacion,
) {
    /** Sin nombre del servidor: «Trabajador ··· abcd» (nunca el UUID completo). */
    val nombreVisible: String get() = nombre ?: "Trabajador ··· ${empleadoId.trim().takeLast(4).lowercase()}"
}
