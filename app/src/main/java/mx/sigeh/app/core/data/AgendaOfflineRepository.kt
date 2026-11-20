package mx.sigeh.app.core.data

import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.model.Agenda

/**
 * Agenda propia **offline de M0** (CU-CAL-19) a partir de la copia guardada (`CachedProgramacion`).
 * Queda detrás de `FUNCIONES_FUTURAS`. La programación del servicio en línea (M2) es
 * [ProgramacionRepository].
 */
interface AgendaOfflineRepository {
    fun observarAgenda(servicioId: String, turnoId: String): Flow<Agenda?>
}
