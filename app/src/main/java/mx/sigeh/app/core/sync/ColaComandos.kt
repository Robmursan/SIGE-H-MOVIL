package mx.sigeh.app.core.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import mx.sigeh.app.core.common.GeneradorIds
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.database.entity.aDominio
import mx.sigeh.app.core.model.AsistenciaPayload
import mx.sigeh.app.core.model.ComandoLocal
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.CommandType
import mx.sigeh.app.core.model.ContextoCaptura
import mx.sigeh.app.core.model.DecisionConflicto
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.IncidenciaPreliminarPayload
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.ResolucionConflictoPayload
import mx.sigeh.app.core.model.TipoIncidencia

/**
 * Cola local de capturas preliminares (Room). Reglas (prompt §7):
 * - El `clientEventId` se genera UNA vez al crear el comando.
 * - Corregir una fila que todavía no se envió actualiza su comando, no crea otro.
 * - Si el comando ya salió del teléfono, la corrección es un comando nuevo (RN-ASI-02).
 */
@Singleton
class ColaComandos @Inject constructor(
    private val dao: PendingCommandDao,
    private val json: Json,
    private val reloj: Reloj,
    private val ids: GeneradorIds,
    private val scheduler: SyncScheduler,
) {

    fun observarTodos(): Flow<List<ComandoLocal>> = dao.observarTodos().map { lista -> lista.map { it.aDominio() } }

    fun observar(clientEventId: String): Flow<ComandoLocal?> = dao.observar(clientEventId).map { it?.aDominio() }

    fun observarPorEnviar(): Flow<Int> = dao.observarConteo(ESTADOS_POR_ENVIAR)

    suspend fun obtener(clientEventId: String): ComandoLocal? = dao.obtener(clientEventId)?.aDominio()

    suspend fun registrarAsistencia(
        contexto: ContextoCaptura,
        jornada: JornadaProgramada,
        estado: EstadoAsistencia,
    ): String {
        val payload = json.encodeToString(
            AsistenciaPayload.serializer(),
            AsistenciaPayload(jornada.programacionId, estado, jornada.nombreCorto),
        )
        val existente = dao.ultimoPara(jornada.programacionId, CommandType.ASISTENCIA)
        if (existente != null && dao.actualizarPayloadSiNoEnviado(existente.clientEventId, payload) == 1) {
            scheduler.programar()
            return existente.clientEventId
        }
        return insertar(contexto, jornada.programacionId, CommandType.ASISTENCIA, payload)
    }

    suspend fun registrarIncidenciaPreliminar(
        contexto: ContextoCaptura,
        jornada: JornadaProgramada,
        tipo: TipoIncidencia,
        jornadaCompleta: Boolean,
        desde: String?,
        hasta: String?,
        observacion: String?,
    ): String {
        val payload = json.encodeToString(
            IncidenciaPreliminarPayload.serializer(),
            IncidenciaPreliminarPayload(
                programacionId = jornada.programacionId,
                tipoIncidenciaId = tipo.id,
                tipoNombre = tipo.nombre,
                jornadaCompleta = jornadaCompleta,
                desde = desde.takeUnless { jornadaCompleta },
                hasta = hasta.takeUnless { jornadaCompleta },
                observacion = observacion?.trim()?.takeIf { it.isNotEmpty() },
                etiqueta = jornada.nombreCorto,
            ),
        )
        return insertar(contexto, jornada.programacionId, CommandType.INCIDENCIA_PRELIMINAR, payload)
    }

    /** Registra la decisión sobre un conflicto para que quede en la auditoría del pase. */
    suspend fun registrarResolucion(
        original: ComandoLocal,
        decision: DecisionConflicto,
        motivo: String?,
        etiqueta: String,
    ): String {
        val payload = json.encodeToString(
            ResolucionConflictoPayload.serializer(),
            ResolucionConflictoPayload(
                programacionId = original.programacionId,
                clientEventIdOriginal = original.clientEventId,
                decision = decision,
                motivo = motivo?.trim()?.takeIf { it.isNotEmpty() },
                etiqueta = etiqueta,
            ),
        )
        val contexto = ContextoCaptura(
            actorId = original.actorId,
            hospitalId = original.hospitalId,
            servicioId = original.servicioId,
            programacionVersion = original.expectedVersion,
        )
        return insertar(contexto, original.programacionId, CommandType.RESOLUCION_CONFLICTO, payload)
    }

    suspend fun eliminar(clientEventId: String) = dao.eliminar(clientEventId)

    suspend fun actualizarEstado(clientEventId: String, estado: CommandStatus, codigo: String?) =
        dao.actualizarEstado(clientEventId, estado, codigo)

    fun reintentarAhora() = scheduler.programar()

    private suspend fun insertar(
        contexto: ContextoCaptura,
        programacionId: String,
        tipo: CommandType,
        payload: String,
    ): String {
        val comando = PendingCommandEntity(
            clientEventId = ids.nuevoId(),
            actorId = contexto.actorId,
            hospitalId = contexto.hospitalId,
            serviceId = contexto.servicioId,
            programacionId = programacionId,
            type = tipo,
            payloadJson = payload,
            expectedVersion = contexto.programacionVersion,
            createdAtDevice = reloj.ahoraMillis(),
            status = CommandStatus.PENDIENTE_LOCAL,
            attemptCount = 0,
            lastErrorCode = null,
        )
        dao.insertar(comando)
        scheduler.programar()
        return comando.clientEventId
    }

    companion object {
        val ESTADOS_POR_ENVIAR: List<CommandStatus> = CommandStatus.entries.filter { it.porEnviar }
    }
}
