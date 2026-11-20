package mx.sigeh.app.core.data

import mx.sigeh.app.core.model.TipoIncidencia

/**
 * INC: catálogo de tipos (CU-INC-01). El registro preliminar (CU-INC-03/07) se encola en Room;
 * desde el teléfono nunca se autoriza una incidencia.
 */
interface IncidenciaRepository {
    suspend fun tiposIncidencia(hospitalId: String): List<TipoIncidencia>
}
