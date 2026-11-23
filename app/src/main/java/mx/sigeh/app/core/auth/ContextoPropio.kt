package mx.sigeh.app.core.auth

import mx.sigeh.app.core.model.RolAlcance

/**
 * Contexto propio vigente de la sesión, tal como lo informa `GET /auth/me/contextos`, ya con
 * nombres legibles. Vive solo en memoria: no se guarda con la sesión cifrada y se vuelve a pedir
 * cada vez que se abre el selector de servicio.
 *
 * [permisos] decide qué se muestra como activo; el servidor vuelve a comprobar facultad, sesión,
 * vigencia y ámbito en cada petición. [rol] es informativo y nunca otorga facultades.
 */
data class ContextoPropio(
    val assignmentId: String,
    val hospitalId: String,
    val hospitalNombre: String,
    /** Zona IANA del hospital (por ejemplo `America/Mexico_City`). */
    val zonaHoraria: String,
    val divisionId: String?,
    val divisionNombre: String?,
    val servicioId: String?,
    val servicioNombre: String?,
    /** TITULAR, ENCARGADO o null si el servidor envía un tipo que la app no conoce. */
    val rol: RolAlcance?,
    /** date-time ISO-8601. */
    val vigenteDesde: String,
    /** date-time ISO-8601; null si no tiene término. */
    val vigenteHasta: String?,
    val permisos: List<String>,
)
