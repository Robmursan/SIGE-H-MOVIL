package mx.sigeh.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.database.entity.CachedProgramacionEntity

@Dao
interface CachedProgramacionDao {

    @Upsert
    suspend fun guardar(programacion: CachedProgramacionEntity)

    @Query("SELECT * FROM cached_programacion WHERE programacionId = :programacionId")
    fun observar(programacionId: String): Flow<CachedProgramacionEntity?>

    @Query("DELETE FROM cached_programacion WHERE expiresAt < :ahoraMillis")
    suspend fun eliminarVencidas(ahoraMillis: Long)

    @Query("DELETE FROM cached_programacion")
    suspend fun eliminarTodas()
}
