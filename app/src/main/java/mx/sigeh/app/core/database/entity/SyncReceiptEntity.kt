package mx.sigeh.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Acuse del servidor por comando aplicado (guía §11). Tabla solo del teléfono. */
@Entity(tableName = "sync_receipt")
data class SyncReceiptEntity(
    @PrimaryKey val clientEventId: String,
    val serverEventId: String?,
    val appliedAtServer: Long?,
    val responseCode: Int,
)
