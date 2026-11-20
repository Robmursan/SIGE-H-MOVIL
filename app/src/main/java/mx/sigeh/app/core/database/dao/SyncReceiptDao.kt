package mx.sigeh.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.database.entity.SyncReceiptEntity

@Dao
interface SyncReceiptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(recibo: SyncReceiptEntity)

    @Query("SELECT * FROM sync_receipt WHERE clientEventId = :clientEventId")
    suspend fun obtener(clientEventId: String): SyncReceiptEntity?

    @Query("SELECT * FROM sync_receipt")
    fun observarTodos(): Flow<List<SyncReceiptEntity>>

    @Query("DELETE FROM sync_receipt")
    suspend fun eliminarTodos()
}
