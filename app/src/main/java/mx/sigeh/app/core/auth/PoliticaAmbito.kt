package mx.sigeh.app.core.auth

import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.RolAlcance

/**
 * Regla provisional de M1 mientras el backend no publique organización (CU-ORG-01/04/07):
 * el catálogo de servicios y turnos sigue siendo Fake, pero las facultades se limitan con lo que
 * `GET /auth/me` informa. Denegar por defecto: sin asignaciones reales no se ofrece ningún ámbito.
 *
 * Todavía no se puede cruzar el servicio Fake con el UUID real (`servicioId`), por eso solo se usa el rol.
 */
object PoliticaAmbito {

    /**
     * @param asignaciones null en modo Fake (sin información del servidor).
     * @return rol con el que se puede actuar, o null si el ámbito no debe ofrecerse.
     */
    fun rolEfectivo(alcance: Alcance, asignaciones: List<AsignacionSesion>?): RolAlcance? = when {
        asignaciones == null -> alcance.rol
        asignaciones.none { it.rol != null } -> null
        alcance.rol == RolAlcance.TITULAR && asignaciones.any { it.rol == RolAlcance.TITULAR } -> RolAlcance.TITULAR
        else -> RolAlcance.ENCARGADO
    }
}
