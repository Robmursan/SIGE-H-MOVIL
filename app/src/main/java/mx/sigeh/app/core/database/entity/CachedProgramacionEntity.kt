package mx.sigeh.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Copia local de la programación (guía §11). Tabla solo del teléfono; se purga al expirar o al cerrar sesión. */
@Entity(tableName = "cached_programacion")
data class CachedProgramacionEntity(
    @PrimaryKey val programacionId: String,
    val hospitalId: String,
    val servicioId: String,
    val startUtc: Long,
    val endUtc: Long,
    val version: Int,
    val fetchedAt: Long,
    val expiresAt: Long,
)
