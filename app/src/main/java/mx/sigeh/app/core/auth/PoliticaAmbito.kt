package mx.sigeh.app.core.auth

import mx.sigeh.app.core.common.HoraHospital
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
 * Opción del selector de servicio, construida solo con lo que informa el servidor para la sesión:
 * los contextos de `/auth/me/contextos` (con nombres) o, como respaldo, las asignaciones de
 * `/auth/me` (solo UUID). Sin [servicioId] (ámbito de hospital o división) se muestra
 * deshabilitada: no se enumeran servicios con `/servicios` ni se inventan descendientes.
 */
data class AmbitoAutorizado(
    val hospitalId: String,
    val divisionId: String?,
    val servicioId: String?,
    /** TITULAR, ENCARGADO o null (el contrato permite `kind: null`). */
    val rol: RolAlcance?,
    val permisos: Set<String>,
    /** Nombres de `/auth/me/contextos`; null si el ámbito salió de `/auth/me`. */
    val hospitalNombre: String? = null,
    val divisionNombre: String? = null,
    val servicioNombre: String? = null,
    /** Zona IANA del hospital; solo la informa `/auth/me/contextos`. */
    val zonaHoraria: String? = null,
    /**
     * Fin de vigencia más lejano de las asignaciones unidas (date-time ISO-8601). Null si alguna no
     * tiene término o si el ámbito salió de `/auth/me`, que no informa vigencias.
     */
    val vigenteHasta: String? = null,
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
                    rol = rolUnido(grupo.map { it.rol }),
                    permisos = grupo.flatMapTo(mutableSetOf()) { it.permisos },
                )
            }
            .filter { it.facultades.consultarPase }
            .sortedBy { !it.habilitado }

    /**
     * Ámbitos del selector a partir de `GET /auth/me/contextos`, con la misma regla que
     * [ambitosAutorizados]: se unen los contextos del mismo ámbito (permisos sumados; TITULAR si
     * alguno lo es), entra un ámbito si tiene `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR` y los que
     * no indican servicio van al final. De cada grupo se toma el primer nombre no vacío, la zona del
     * hospital y la vigencia más lejana. Sin contextos no se ofrece nada (denegar por defecto).
     */
    fun ambitosDeContextos(contextos: List<ContextoPropio>): List<AmbitoAutorizado> =
        contextos
            .groupBy { Triple(it.hospitalId, it.divisionId, it.servicioId) }
            .map { (ambito, grupo) ->
                AmbitoAutorizado(
                    hospitalId = ambito.first,
                    divisionId = ambito.second,
                    servicioId = ambito.third,
                    rol = rolUnido(grupo.map { it.rol }),
                    permisos = grupo.flatMapTo(mutableSetOf()) { it.permisos },
                    hospitalNombre = grupo.firstNotNullOfOrNull { c -> c.hospitalNombre.takeIf { it.isNotBlank() } },
                    divisionNombre = grupo.firstNotNullOfOrNull { c -> c.divisionNombre?.takeIf { it.isNotBlank() } },
                    servicioNombre = grupo.firstNotNullOfOrNull { c -> c.servicioNombre?.takeIf { it.isNotBlank() } },
                    zonaHoraria = grupo.firstNotNullOfOrNull { c -> c.zonaHoraria.takeIf { it.isNotBlank() } },
                    vigenteHasta = vigenciaUnida(grupo.map { it.vigenteHasta }),
                )
            }
            .filter { it.facultades.consultarPase }
            .sortedBy { !it.habilitado }

    private fun rolUnido(roles: List<RolAlcance?>): RolAlcance? = when {
        RolAlcance.TITULAR in roles -> RolAlcance.TITULAR
        RolAlcance.ENCARGADO in roles -> RolAlcance.ENCARGADO
        else -> null
    }

    /**
     * El backend autoriza con cualquier asignación vigente del ámbito, así que el ámbito dura lo que
     * la más lejana. Si alguna no tiene término, o una fecha no se puede leer, no se muestra vigencia.
     */
    private fun vigenciaUnida(fines: List<String?>): String? {
        if (fines.isEmpty() || fines.any { it == null }) return null
        val conInstante = fines.filterNotNull().map { it to HoraHospital.instante(it) }
        if (conInstante.any { it.second == null }) return null
        return conInstante.maxByOrNull { it.second!! }?.first
    }

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
