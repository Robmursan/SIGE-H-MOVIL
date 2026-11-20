package mx.sigeh.app.core.data

import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType

/**
 * Pasarela remota de sincronización (CU-ASI-13 y CU-OPS-04). El servidor responde por elemento:
 * aplicado, ya aplicado, conflicto o denegado (FC-03). La cola local vive en `ColaComandos`.
 */
interface SyncRepository {
    suspend fun enviarLote(comandos: List<ComandoSync>): ResultadoLote
}

/** Lo que viaja al servidor. `clientEventId` se envía también como `Idempotency-Key`. */
data class ComandoSync(
    val clientEventId: String,
    val actorId: String,
    val hospitalId: String,
    val servicioId: String,
    val programacionId: String,
    val tipo: CommandType,
    val payloadJson: String,
    val expectedVersion: Int?,
)

sealed interface ResultadoLote {
    data class Respuestas(val elementos: List<ResultadoElemento>) : ResultadoLote

    /** 401 o 403 global: la sesión venció. Se detiene la cola, sin reintentos en bucle. */
    data class NoAutorizado(val httpCode: Int) : ResultadoLote

    /** Red caída, 429 o 5xx: se reintenta con backoff exponencial. */
    data class ErrorTransitorio(val codigo: String) : ResultadoLote
}

data class ResultadoElemento(
    val clientEventId: String,
    val estado: CommandStatus,
    val httpCode: Int,
    val serverEventId: String? = null,
    val aplicadoEnServidorMillis: Long? = null,
    val codigo: String? = null,
)
