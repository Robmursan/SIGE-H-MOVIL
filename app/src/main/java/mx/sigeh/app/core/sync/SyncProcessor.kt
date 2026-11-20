package mx.sigeh.app.core.sync

import java.io.IOException
import javax.inject.Inject
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.data.ComandoSync
import mx.sigeh.app.core.data.ResultadoLote
import mx.sigeh.app.core.data.SyncRepository
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.dao.SyncReceiptDao
import mx.sigeh.app.core.database.entity.PendingCommandEntity
import mx.sigeh.app.core.database.entity.SyncReceiptEntity
import mx.sigeh.app.core.model.CommandStatus

enum class SyncOutcome { COMPLETADO, REINTENTAR, SESION_VENCIDA }

/**
 * Lógica de envío de la cola (la usa [SyncWorker]). Separada del Worker para probarla en JVM.
 *
 * - Reenvía siempre con el mismo `clientEventId` (Idempotency-Key): un reenvío nunca duplica.
 * - 401/403 global: marca la sesión como vencida y detiene la cola. No reintenta en bucle.
 * - Nunca sobrescribe un registro validado: el conflicto queda visible para que la persona decida.
 */
class SyncProcessor @Inject constructor(
    private val dao: PendingCommandDao,
    private val receiptDao: SyncReceiptDao,
    private val remoto: SyncRepository,
    private val sessionStore: SessionStore,
) {

    suspend fun procesar(): SyncOutcome {
        val sesion = sessionStore.actual()
        if (sesion == null || sesion.vencida) return SyncOutcome.SESION_VENCIDA

        val lote = dao.obtenerPorEstado(ENVIABLES)
        if (lote.isEmpty()) return SyncOutcome.COMPLETADO

        dao.marcarEnviando(lote.map { it.clientEventId })

        val resultado = try {
            remoto.enviarLote(lote.map { it.aComandoSync() })
        } catch (_: IOException) {
            ResultadoLote.ErrorTransitorio(CODIGO_RED)
        }

        return when (resultado) {
            is ResultadoLote.NoAutorizado -> {
                lote.forEach {
                    dao.actualizarEstado(it.clientEventId, CommandStatus.ERROR_REINTENTABLE, "HTTP_${resultado.httpCode}")
                }
                sessionStore.marcarVencida()
                SyncOutcome.SESION_VENCIDA
            }

            is ResultadoLote.ErrorTransitorio -> {
                lote.forEach { dao.actualizarEstado(it.clientEventId, CommandStatus.ERROR_REINTENTABLE, resultado.codigo) }
                SyncOutcome.REINTENTAR
            }

            is ResultadoLote.Respuestas -> {
                val porId = resultado.elementos.associateBy { it.clientEventId }
                var reintentar = false
                lote.forEach { comando ->
                    val respuesta = porId[comando.clientEventId]
                    if (respuesta == null) {
                        dao.actualizarEstado(comando.clientEventId, CommandStatus.ERROR_REINTENTABLE, CODIGO_SIN_RESPUESTA)
                        reintentar = true
                    } else {
                        dao.actualizarEstado(comando.clientEventId, respuesta.estado, respuesta.codigo)
                        if (respuesta.estado.aplicado) {
                            receiptDao.insertar(
                                SyncReceiptEntity(
                                    clientEventId = comando.clientEventId,
                                    serverEventId = respuesta.serverEventId,
                                    appliedAtServer = respuesta.aplicadoEnServidorMillis,
                                    responseCode = respuesta.httpCode,
                                ),
                            )
                        }
                        if (respuesta.estado == CommandStatus.ERROR_REINTENTABLE) reintentar = true
                    }
                }
                if (reintentar) SyncOutcome.REINTENTAR else SyncOutcome.COMPLETADO
            }
        }
    }

    private fun PendingCommandEntity.aComandoSync() = ComandoSync(
        clientEventId = clientEventId,
        actorId = actorId,
        hospitalId = hospitalId,
        servicioId = serviceId,
        programacionId = programacionId,
        tipo = type,
        payloadJson = payloadJson,
        expectedVersion = expectedVersion,
    )

    companion object {
        /** ENVIANDO se incluye por si el proceso murió a mitad de un envío: reenviar es seguro. */
        val ENVIABLES = listOf(CommandStatus.PENDIENTE_LOCAL, CommandStatus.ERROR_REINTENTABLE, CommandStatus.ENVIANDO)
        const val CODIGO_RED = "RED_NO_DISPONIBLE"
        const val CODIGO_SIN_RESPUESTA = "SIN_RESPUESTA"
    }
}
