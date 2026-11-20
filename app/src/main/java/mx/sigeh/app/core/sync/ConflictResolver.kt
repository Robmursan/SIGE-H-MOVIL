package mx.sigeh.app.core.sync

import javax.inject.Inject
import mx.sigeh.app.core.model.CommandStatus
import mx.sigeh.app.core.model.DecisionConflicto

/**
 * Aplica la decisión de la persona sobre un conflicto (RN-ASI-05, CU-ASI-14).
 * La app nunca reemplaza un registro validado: solo conserva lo del servidor o pide corrección.
 * En ambos casos se encola un comando RESOLUCION_CONFLICTO para la auditoría del pase.
 */
class ConflictResolver @Inject constructor(
    private val cola: ColaComandos,
) {

    suspend fun resolver(
        clientEventId: String,
        decision: DecisionConflicto,
        motivo: String?,
        etiqueta: String,
    ): Boolean {
        val original = cola.obtener(clientEventId) ?: return false
        if (original.estado != CommandStatus.CONFLICTO) return false
        if (decision == DecisionConflicto.SOLICITAR_CORRECCION && motivo.isNullOrBlank()) return false

        cola.registrarResolucion(original, decision, motivo, etiqueta)
        when (decision) {
            // La captura local se descarta; la constancia queda en el comando de resolución.
            DecisionConflicto.CONSERVAR_SERVIDOR -> cola.eliminar(clientEventId)
            // La captura queda en revisión: Supervisión decide.
            DecisionConflicto.SOLICITAR_CORRECCION ->
                cola.actualizarEstado(clientEventId, CommandStatus.REQUIERE_REVISION, CODIGO_CORRECCION)
        }
        return true
    }

    companion object {
        const val CODIGO_CORRECCION = "CORRECCION_SOLICITADA"
    }
}
