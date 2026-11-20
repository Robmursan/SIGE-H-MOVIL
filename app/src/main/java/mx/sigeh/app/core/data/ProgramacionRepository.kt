package mx.sigeh.app.core.data

import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.model.Agenda

/** CAL: agenda propia (CU-CAL-19) a partir de la copia guardada (`CachedProgramacion`). */
interface ProgramacionRepository {
    fun observarAgenda(servicioId: String, turnoId: String): Flow<Agenda?>
}
