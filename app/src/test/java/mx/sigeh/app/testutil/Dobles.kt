package mx.sigeh.app.testutil

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.GeneradorIds
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.dao.SyncReceiptDao
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.database.entity.SyncReceiptEntity
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.sync.ConnectivityObserver
import mx.sigeh.app.core.sync.SyncScheduler
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Sustituye Dispatchers.Main para los ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = kotlinx.coroutines.Dispatchers.resetMain()
}

class RelojFijo(var ahora: Long) : Reloj {
    override fun ahoraMillis(): Long = ahora
}

class IdsSecuenciales : GeneradorIds {
    private var siguiente = 0
    override fun nuevoId(): String = "00000000-0000-4000-8000-%012d".format(++siguiente)
}

class SchedulerContador : SyncScheduler {
    var programaciones = 0
        private set
    var cancelaciones = 0
        private set

    override fun programar() {
        programaciones++
    }

    override fun cancelar() {
        cancelaciones++
    }
}

class ConectividadFalsa(enLinea: Boolean) : ConnectivityObserver {
    override val enLinea = MutableStateFlow(enLinea)
    override val sinConexionDesde: StateFlow<Long?> = MutableStateFlow(null)
}

class SessionStoreEnMemoria(inicial: Session?) : SessionStore {
    private val estado = MutableStateFlow(inicial)
    override val session: Flow<Session?> = estado
    override suspend fun actual(): Session? = estado.value
    override fun sesionEnMemoria(): Session? = estado.value
    override suspend fun guardar(session: Session) {
        estado.value = session
    }

    override suspend fun marcarVencida() {
        estado.value = estado.value?.copy(vencida = true)
    }

    override suspend fun limpiar() {
        estado.value = null
    }
}

/** DAO en memoria con la misma semántica que las consultas de Room. */
class PendingCommandDaoFalso : PendingCommandDao {
    val filas = MutableStateFlow<List<PendingCommandEntity>>(emptyList())

    override fun observarTodos(): Flow<List<PendingCommandEntity>> =
        filas.map { lista -> lista.sortedByDescending { it.createdAtDevice } }

    override fun observarPorEstado(estados: List<CommandStatus>): Flow<List<PendingCommandEntity>> =
        filas.map { lista -> lista.filter { it.status in estados }.sortedBy { it.createdAtDevice } }

    override suspend fun obtenerPorEstado(estados: List<CommandStatus>): List<PendingCommandEntity> =
        filas.value.filter { it.status in estados }.sortedBy { it.createdAtDevice }

    override suspend fun obtener(clientEventId: String): PendingCommandEntity? =
        filas.value.firstOrNull { it.clientEventId == clientEventId }

    override fun observar(clientEventId: String): Flow<PendingCommandEntity?> =
        filas.map { lista -> lista.firstOrNull { it.clientEventId == clientEventId } }

    override suspend fun ultimoPara(programacionId: String, tipo: CommandType): PendingCommandEntity? =
        filas.value.filter { it.programacionId == programacionId && it.type == tipo }.maxByOrNull { it.createdAtDevice }

    override suspend fun insertar(comando: PendingCommandEntity) {
        check(filas.value.none { it.clientEventId == comando.clientEventId }) { "clientEventId duplicado" }
        filas.value = filas.value + comando
    }

    override suspend fun actualizarPayloadSiNoEnviado(clientEventId: String, payloadJson: String): Int {
        val actual = filas.value.firstOrNull {
            it.clientEventId == clientEventId && it.status == CommandStatus.PENDIENTE_LOCAL && it.attemptCount == 0
        } ?: return 0
        reemplazar(actual.copy(payloadJson = payloadJson))
        return 1
    }

    override suspend fun marcarEnviando(ids: List<String>) {
        filas.value = filas.value.map {
            if (it.clientEventId in ids) it.copy(status = CommandStatus.ENVIANDO, attemptCount = it.attemptCount + 1) else it
        }
    }

    override suspend fun actualizarEstado(clientEventId: String, estado: CommandStatus, codigo: String?) {
        filas.value = filas.value.map {
            if (it.clientEventId == clientEventId) it.copy(status = estado, lastErrorCode = codigo) else it
        }
    }

    override suspend fun eliminar(clientEventId: String) {
        filas.value = filas.value.filterNot { it.clientEventId == clientEventId }
    }

    override suspend fun contarDeOtroActor(actorId: String): Int = filas.value.count { it.actorId != actorId }

    override fun observarConteo(estados: List<CommandStatus>): Flow<Int> =
        filas.map { lista -> lista.count { it.status in estados } }

    override suspend fun contar(): Int = filas.value.size

    override suspend fun eliminarTodos() {
        filas.value = emptyList()
    }

    private fun reemplazar(comando: PendingCommandEntity) {
        filas.value = filas.value.map { if (it.clientEventId == comando.clientEventId) comando else it }
    }
}

class SyncReceiptDaoFalso : SyncReceiptDao {
    val filas = MutableStateFlow<Map<String, SyncReceiptEntity>>(emptyMap())

    override suspend fun insertar(recibo: SyncReceiptEntity) {
        filas.value = filas.value + (recibo.clientEventId to recibo)
    }

    override suspend fun obtener(clientEventId: String): SyncReceiptEntity? = filas.value[clientEventId]

    override fun observarTodos(): Flow<List<SyncReceiptEntity>> = filas.map { it.values.toList() }

    override suspend fun eliminarTodos() {
        filas.value = emptyMap()
    }
}
