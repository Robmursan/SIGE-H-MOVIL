package mx.sigeh.app.core.model

import kotlinx.serialization.Serializable

/** Módulo ORG: hospital → división → servicio (→ subárea opcional, fuera de M0). */
data class Hospital(
    val id: String,
    val nombre: String,
)

data class Servicio(
    val id: String,
    val nombre: String,
    val divisionId: String,
    val divisionNombre: String,
    val hospitalId: String,
)

/**
 * Turno laboral (módulo CAL). `horarios` admite varios intervalos porque la jornada
 * acumulada tiene dos variantes (07:00–19:00 y 09:00–21:00).
 */
data class Turno(
    val id: String,
    val nombre: String,
    val horarios: List<String>,
) {
    val horarioTexto: String get() = horarios.joinToString(" · ")
}

/** Rol con el que la persona actúa en un servicio (spec §3): titular o encargado de turno. */
@Serializable
enum class RolAlcance { TITULAR, ENCARGADO }

/**
 * Alcance vigente (CU-IAM-05): hospital, servicio, rol y vigencia.
 * Un alcance vencido no se ofrece (FC-01).
 */
data class Alcance(
    val id: String,
    val hospital: Hospital,
    val servicio: Servicio,
    val rol: RolAlcance,
    val cargo: String,
    val vigenteHastaMillis: Long?,
) {
    fun estaVigente(ahoraMillis: Long): Boolean =
        vigenteHastaMillis == null || ahoraMillis < vigenteHastaMillis

    val facultades: Facultades get() = Facultades.para(rol)
}

/**
 * Facultades visibles en la interfaz. El backend es quien protege de verdad; aquí solo se
 * evita mostrar como activas las acciones sin facultad. Las facultades exactas del encargado
 * siguen pendientes (spec §16, decisión 2): se toma el actor de cada CU como referencia.
 */
data class Facultades(
    val capturarPase: Boolean,
    val registrarIncidencia: Boolean,
    val registrarSalida: Boolean,
    val recibirEventual: Boolean,
) {
    companion object {
        fun para(rol: RolAlcance): Facultades = when (rol) {
            RolAlcance.TITULAR -> Facultades(
                capturarPase = true,
                registrarIncidencia = true,
                registrarSalida = true,
                recibirEventual = true,
            )
            // CU-COB-10 lo realiza el "Jefe de servicio receptor": el encargado no lo ve activo.
            RolAlcance.ENCARGADO -> Facultades(
                capturarPase = true,
                registrarIncidencia = true,
                registrarSalida = true,
                recibirEventual = false,
            )
        }
    }
}

data class Usuario(
    val id: String,
    val nombreCorto: String,
)

/**
 * Servicio y turno elegidos en el selector de ámbito. Vive solo en memoria (ScopeCache).
 * [rolEfectivo] puede ser menor que el rol del catálogo: en modo IAM real nunca supera al que
 * informa `GET /auth/me`. Las facultades visibles salen de él, no del catálogo.
 */
data class AmbitoActivo(
    val alcance: Alcance,
    val turno: Turno,
    val rolEfectivo: RolAlcance = alcance.rol,
) {
    val facultades: Facultades get() = Facultades.para(rolEfectivo)
}
