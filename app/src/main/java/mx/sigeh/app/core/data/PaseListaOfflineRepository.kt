package mx.sigeh.app.core.data

import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.PaseLista
import mx.sigeh.app.core.model.RegistroAsistenciaServidor

/**
 * Pase de lista **offline de M0** (modelo provisional, solo Fake): borrador (CU-ASI-05), envío a
 * validación (CU-ASI-06) y capturas encoladas en `ColaComandos` (CU-ASI-13). Lo usan las pantallas
 * que quedan detrás de `FUNCIONES_FUTURAS` hasta que exista el bloque B7 del backend.
 *
 * El pase en línea contra el contrato real es [PaseListaRepository] (M2).
 */
interface PaseListaOfflineRepository {
    /** Pase del servicio y turno. Emite `null` si no hay programación para ese ámbito. */
    fun observarPase(servicioId: String, turnoId: String): Flow<PaseLista?>

    fun observarRegistroServidor(programacionId: String): Flow<RegistroAsistenciaServidor?>

    suspend fun jornada(programacionId: String): JornadaProgramada?

    suspend fun guardarBorrador(paseId: String)

    suspend fun solicitarEnvioAValidacion(paseId: String, solicitadoEnMillis: Long)
}
