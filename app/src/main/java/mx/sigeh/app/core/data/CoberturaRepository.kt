package mx.sigeh.app.core.data

import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.model.AsignacionOperativa
import mx.sigeh.app.core.model.NecesidadCobertura

/**
 * COB: necesidades del servicio (solo lectura en el teléfono) e incorporación de eventuales
 * (CU-COB-10/11). Recibir eventuales requiere conexión.
 */
interface CoberturaRepository {
    fun observarNecesidades(servicioId: String): Flow<List<NecesidadCobertura>>

    fun observarAsignacion(asignacionId: String): Flow<AsignacionOperativa?>

    suspend fun confirmarIncorporacion(asignacionId: String, inicioEfectivo: String): Boolean

    suspend fun rechazarIncorporacion(asignacionId: String, motivo: String): Boolean
}
