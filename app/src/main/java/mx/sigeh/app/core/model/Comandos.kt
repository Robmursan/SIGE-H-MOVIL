package mx.sigeh.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Estados de un comando móvil (spec §6.2 y guía §11). */
enum class CommandStatus {
    PENDIENTE_LOCAL,
    ENVIANDO,
    APLICADO,
    YA_APLICADO,
    CONFLICTO,
    DENEGADO,
    REQUIERE_REVISION,
    ERROR_REINTENTABLE,
    ;

    /** Lo que todavía no está resuelto en el servidor y cuenta para el chip "N por enviar". */
    val porEnviar: Boolean
        get() = this == PENDIENTE_LOCAL || this == ENVIANDO || this == ERROR_REINTENTABLE || this == CONFLICTO

    val aplicado: Boolean get() = this == APLICADO || this == YA_APLICADO
}

/** Capturas preliminares permitidas sin red (prompt §7): asistencia, incidencia preliminar y resolución. */
enum class CommandType { ASISTENCIA, INCIDENCIA_PRELIMINAR, RESOLUCION_CONFLICTO }

/** Vista de dominio de un `PendingCommand` de Room. */
data class ComandoLocal(
    val clientEventId: String,
    val actorId: String,
    val hospitalId: String,
    val servicioId: String,
    val programacionId: String,
    val tipo: CommandType,
    val payloadJson: String,
    val expectedVersion: Int?,
    val creadoEnMillis: Long,
    val estado: CommandStatus,
    val intentos: Int,
    val ultimoError: String?,
)

/** Contexto con el que se crea un comando: actor y ámbito salen de la sesión, nunca del cuerpo. */
data class ContextoCaptura(
    val actorId: String,
    val hospitalId: String,
    val servicioId: String,
    val programacionVersion: Int?,
)

/*
 * Cargas útiles locales. NO son DTO del backend: el contrato real llegará con el OpenAPI (M1).
 * `etiqueta` es solo para mostrar el registro en el teléfono (nombre abreviado, sin RUD completo
 * ni datos sensibles) y deberá retirarse al mapear al DTO real.
 */

@Serializable
data class AsistenciaPayload(
    val programacionId: String,
    val estado: EstadoAsistencia,
    val etiqueta: String,
)

@Serializable
data class IncidenciaPreliminarPayload(
    val programacionId: String,
    val tipoIncidenciaId: String,
    val tipoNombre: String,
    val jornadaCompleta: Boolean,
    val desde: String? = null,
    val hasta: String? = null,
    val observacion: String? = null,
    val etiqueta: String,
)

@Serializable
enum class DecisionConflicto { CONSERVAR_SERVIDOR, SOLICITAR_CORRECCION }

@Serializable
data class ResolucionConflictoPayload(
    val programacionId: String,
    val clientEventIdOriginal: String,
    val decision: DecisionConflicto,
    val motivo: String? = null,
    val etiqueta: String,
)

/** Configuración JSON compartida (también la provee Hilt). */
val SigehJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
