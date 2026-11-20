package mx.sigeh.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.ComandoLocal

/**
 * Cola local de comandos (guía §11). Tabla solo del teléfono: NO existe en PostgreSQL V1.
 * `clientEventId` se genera una vez y se reutiliza como `Idempotency-Key` en cada reintento.
 */
@Entity(
    tableName = "pending_command",
    indices = [
        Index("status"),
        Index(value = ["programacionId", "type"]),
    ],
)
data class PendingCommandEntity(
    @PrimaryKey val clientEventId: String,
    val actorId: String,
    val hospitalId: String,
    val serviceId: String,
    val programacionId: String,
    val type: CommandType,
    val payloadJson: String,
    val expectedVersion: Int?,
    val createdAtDevice: Long,
    val status: CommandStatus,
    val attemptCount: Int,
    val lastErrorCode: String?,
)

fun PendingCommandEntity.aDominio(): ComandoLocal = ComandoLocal(
    clientEventId = clientEventId,
    actorId = actorId,
    hospitalId = hospitalId,
    servicioId = serviceId,
    programacionId = programacionId,
    tipo = type,
    payloadJson = payloadJson,
    expectedVersion = expectedVersion,
    creadoEnMillis = createdAtDevice,
    estado = status,
    intentos = attemptCount,
    ultimoError = lastErrorCode,
)
