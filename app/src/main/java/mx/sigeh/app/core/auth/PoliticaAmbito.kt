package mx.sigeh.app.core.auth

import mx.sigeh.app.core.model.Alcance
import mx.sigeh.app.core.model.RolAlcance

/**
 * Códigos de permiso que envía `GET /auth/me` (`ProfileDto.assignments[].permissions`) según
 * SIGE-H-WEB main 2d1ec62. No se inventan otros: incidencias, movimientos y cobertura aún no
 * tienen código y por eso quedan detrás de `FUNCIONES_FUTURAS`.
 */
object CodigosPermiso {
    const val ASISTENCIA_CAPTURAR = "ASISTENCIA_CAPTURAR"
    const val ASISTENCIA_VALIDAR = "ASISTENCIA_VALIDAR"
    const val PERSONAL_CONSULTAR = "PERSONAL_CONSULTAR"
    const val PROGRAMACION_CONSULTAR = "PROGRAMACION_CONSULTAR"
    const val TABLERO_CONSULTAR = "TABLERO_CONSULTAR"
    const val CATALOGOS_ADMINISTRAR = "CATALOGOS_ADMINISTRAR"
}

/**
 * Facultades de M2 en un ámbito. Salen **solo** de los códigos de permiso, nunca del nombre del
 * rol (TITULAR/ENCARGADO). El backend vuelve a comprobarlas en cada petición: aquí solo se decide
 * qué mostrar como activo.
 */
data class FacultadesAsistencia(
    /** `ASISTENCIA_CAPTURAR`: botones de captura y chip «Capturar». */
    val capturar: Boolean,
    /** `ASISTENCIA_VALIDAR`: chip «Consultar». En la app solo da lectura del pase. */
    val validar: Boolean,
    /** `PROGRAMACION_CONSULTAR`: pestaña Programación. */
    val consultarProgramacion: Boolean,
    /** `PERSONAL_CONSULTAR`: nombres en Programación (`GET /empleados`). */
    val consultarPersonal: Boolean,
) {
    /** `GET /pases-lista` exige `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`. */
    val consultarPase: Boolean get() = capturar || validar

    companion object {
        fun de(permisos: Collection<String>) = FacultadesAsistencia(
            capturar = CodigosPermiso.ASISTENCIA_CAPTURAR in permisos,
            validar = CodigosPermiso.ASISTENCIA_VALIDAR in permisos,
            consultarProgramacion = CodigosPermiso.PROGRAMACION_CONSULTAR in permisos,
            consultarPersonal = CodigosPermiso.PERSONAL_CONSULTAR in permisos,
        )
    }
}

/**
 * Opción del selector de servicio en M2, construida solo con las asignaciones de `/auth/me`.
 * Sin [servicioId] (ámbito de hospital o división) se muestra deshabilitada: no se enumeran
 * servicios con `/servicios`.
 */
data class AmbitoAutorizado(
    val hospitalId: String,
    val divisionId: String?,
    val servicioId: String?,
    /** TITULAR, ENCARGADO o null (el contrato permite `kind: null`). */
    val rol: RolAlcance?,
    val permisos: Set<String>,
) {
    val facultades: FacultadesAsistencia get() = FacultadesAsistencia.de(permisos)

    /** Se puede abrir el pase: indica un servicio concreto. */
    val habilitado: Boolean get() = servicioId != null
}

object PoliticaAmbito {

    /**
     * Ámbitos que ofrece el selector de M2 (modo real y Fake, ambos con asignaciones):
     * - las asignaciones del mismo ámbito se unen (permisos sumados; TITULAR si alguna lo es),
     *   porque el backend autoriza cada permiso con cualquier asignación vigente del ámbito;
     * - entra un ámbito si, ya unido, tiene `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`;
     * - los deshabilitados (sin servicio) van al final; el resto conserva el orden del servidor.
     * Sin asignaciones no se ofrece nada (denegar por defecto).
     */
    fun ambitosAutorizados(asignaciones: List<AsignacionSesion>): List<AmbitoAutorizado> =
        asignaciones
            .groupBy { Triple(it.hospitalId, it.divisionId, it.servicioId) }
            .map { (ambito, grupo) ->
                AmbitoAutorizado(
                    hospitalId = ambito.first,
                    divisionId = ambito.second,
                    servicioId = ambito.third,
                    rol = when {
                        grupo.any { it.rol == RolAlcance.TITULAR } -> RolAlcance.TITULAR
                        grupo.any { it.rol == RolAlcance.ENCARGADO } -> RolAlcance.ENCARGADO
                        else -> null
                    },
                    permisos = grupo.flatMapTo(mutableSetOf()) { it.permisos },
                )
            }
            .filter { it.facultades.consultarPase }
            .sortedBy { !it.habilitado }

    /** Facultades de la sesión en un servicio concreto; sin asignación que lo cubra, ninguna. */
    fun facultadesEn(servicioId: String, asignaciones: List<AsignacionSesion>): FacultadesAsistencia =
        FacultadesAsistencia.de(asignaciones.filter { it.servicioId == servicioId }.flatMap { it.permisos })

    /**
     * Regla de M1 para el catálogo Fake de M0 (selector anterior, se retira en M2-F4): las
     * facultades se limitan con el rol que informa `/auth/me`.
     *
     * @param asignaciones null si el servidor no informó nada (sesión guardada por M0).
     * @return rol con el que se puede actuar, o null si el ámbito no debe ofrecerse.
     */
    fun rolEfectivo(alcance: Alcance, asignaciones: List<AsignacionSesion>?): RolAlcance? = when {
        asignaciones == null -> alcance.rol
        asignaciones.none { it.rol != null } -> null
        alcance.rol == RolAlcance.TITULAR && asignaciones.any { it.rol == RolAlcance.TITULAR } -> RolAlcance.TITULAR
        else -> RolAlcance.ENCARGADO
    }
}
