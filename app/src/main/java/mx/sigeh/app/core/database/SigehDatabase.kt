package mx.sigeh.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sigeh.app.core.database.dao.CachedProgramacionDao
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.dao.SyncReceiptDao
import mx.sigeh.app.core.database.entity.CachedProgramacionEntity
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.database.entity.SyncReceiptEntity
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType

/**
 * Base local del teléfono. Esquema exportado en app/schemas. Sin migraciones destructivas:
 * cualquier cambio de versión necesita una migración explícita.
 */
@Database(
    entities = [
        PendingCommandEntity::class,
        CachedProgramacionEntity::class,
        SyncReceiptEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(SigehConverters::class)
abstract class SigehDatabase : RoomDatabase() {
    abstract fun pendingCommandDao(): PendingCommandDao
    abstract fun cachedProgramacionDao(): CachedProgramacionDao
    abstract fun syncReceiptDao(): SyncReceiptDao

    companion object {
        const val NOMBRE = "sigeh.db"
    }
}

/** Los enums se guardan por nombre; las consultas con literales ('PENDIENTE_LOCAL') dependen de esto. */
class SigehConverters {
    @TypeConverter
    fun estadoATexto(estado: CommandStatus): String = estado.name

    @TypeConverter
    fun textoAEstado(texto: String): CommandStatus = CommandStatus.valueOf(texto)

    @TypeConverter
    fun tipoATexto(tipo: CommandType): String = tipo.name

    @TypeConverter
    fun textoATipo(texto: String): CommandType = CommandType.valueOf(texto)
}

/** Borra todo lo guardado en el teléfono (cerrar sesión o alcance vencido). */
interface PurgaLocal {
    suspend fun purgarTodo()
}

class RoomPurgaLocal @Inject constructor(
    private val database: SigehDatabase,
) : PurgaLocal {
    override suspend fun purgarTodo() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}
