package mx.sigeh.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType

@Dao
interface PendingCommandDao {

    @Query("SELECT * FROM pending_command ORDER BY createdAtDevice DESC")
    fun observarTodos(): Flow<List<PendingCommandEntity>>

    @Query("SELECT * FROM pending_command WHERE status IN (:estados) ORDER BY createdAtDevice ASC")
    fun observarPorEstado(estados: List<CommandStatus>): Flow<List<PendingCommandEntity>>

    @Query("SELECT * FROM pending_command WHERE status IN (:estados) ORDER BY createdAtDevice ASC")
    suspend fun obtenerPorEstado(estados: List<CommandStatus>): List<PendingCommandEntity>

    @Query("SELECT * FROM pending_command WHERE clientEventId = :clientEventId")
    suspend fun obtener(clientEventId: String): PendingCommandEntity?

    @Query("SELECT * FROM pending_command WHERE clientEventId = :clientEventId")
    fun observar(clientEventId: String): Flow<PendingCommandEntity?>

    @Query(
        "SELECT * FROM pending_command WHERE programacionId = :programacionId AND type = :tipo " +
            "ORDER BY createdAtDevice DESC LIMIT 1",
    )
    suspend fun ultimoPara(programacionId: String, tipo: CommandType): PendingCommandEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(comando: PendingCommandEntity)

    /**
     * Corrige la carga útil solo si el comando nunca salió del teléfono. Devuelve las filas
     * afectadas: 0 significa que ya se intentó enviar y hay que crear un comando nuevo.
     */
    @Query(
        "UPDATE pending_command SET payloadJson = :payloadJson " +
            "WHERE clientEventId = :clientEventId AND status = 'PENDIENTE_LOCAL' AND attemptCount = 0",
    )
    suspend fun actualizarPayloadSiNoEnviado(clientEventId: String, payloadJson: String): Int

    @Query(
        "UPDATE pending_command SET status = 'ENVIANDO', attemptCount = attemptCount + 1 " +
            "WHERE clientEventId IN (:ids)",
    )
    suspend fun marcarEnviando(ids: List<String>)

    @Query("UPDATE pending_command SET status = :estado, lastErrorCode = :codigo WHERE clientEventId = :clientEventId")
    suspend fun actualizarEstado(clientEventId: String, estado: CommandStatus, codigo: String?)

    @Query("DELETE FROM pending_command WHERE clientEventId = :clientEventId")
    suspend fun eliminar(clientEventId: String)

    @Query("SELECT COUNT(*) FROM pending_command WHERE actorId != :actorId")
    suspend fun contarDeOtroActor(actorId: String): Int

    @Query("SELECT COUNT(*) FROM pending_command WHERE status IN (:estados)")
    fun observarConteo(estados: List<CommandStatus>): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_command")
    suspend fun contar(): Int

    @Query("DELETE FROM pending_command")
    suspend fun eliminarTodos()
}
