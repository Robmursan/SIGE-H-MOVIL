package mx.sigeh.app.core.data

import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.PaseLista
import mx.sigeh.app.core.model.RegistroAsistenciaServidor

/**
 * ASI: pase de lista (CU-ASI-01), borrador (CU-ASI-05) y envío a validación (CU-ASI-06).
 * Las capturas NO pasan por aquí: se encolan en `ColaComandos` (Room) y se sincronizan con CU-ASI-13.
 */
interface PaseListaRepository {
    /** Pase del servicio y turno. Emite `null` si no hay programación para ese ámbito. */
    fun observarPase(servicioId: String, turnoId: String): Flow<PaseLista?>

    fun observarRegistroServidor(programacionId: String): Flow<RegistroAsistenciaServidor?>

    suspend fun jornada(programacionId: String): JornadaProgramada?

    suspend fun guardarBorrador(paseId: String)

    suspend fun solicitarEnvioAValidacion(paseId: String, solicitadoEnMillis: Long)
}
