package mx.sigeh.app.core.data

import mx.sigeh.app.core.auth.ContextoPropio

/**
 * IAM: contextos propios vigentes con nombres (`GET /auth/me/contextos`, operationId `iamOwnContexts`).
 * Sustituye a las etiquetas por identificador del selector cuando el backend la tiene disponible.
 */
interface ContextosRepository {
    suspend fun contextosPropios(): ResultadoContextos
}

sealed interface ResultadoContextos {

    /** 200. La lista puede venir vacía: la sesión no tiene contextos vigentes. */
    data class Exito(val contextos: List<ContextoPropio>) : ResultadoContextos

    /** 401 aun después de la única renovación coordinada: hay que iniciar sesión de nuevo. */
    data object SesionVencida : ResultadoContextos

    /**
     * 400, 403, 404, 503 («Contextos no disponibles» o «IAM no disponible»), cuerpo ilegible u otro
     * código. La ruta está publicada pero aún espera validación SQL: quien llama vuelve a las
     * asignaciones de `/auth/me` con etiquetas por identificador.
     *
     * @param httpCode código recibido; null si el cuerpo no se pudo leer.
     */
    data class NoDisponible(val httpCode: Int?) : ResultadoContextos

    data object SinConexion : ResultadoContextos
}
