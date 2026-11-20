package mx.sigeh.app.core.data

import kotlinx.coroutines.flow.Flow
import mx.sigeh.app.core.model.ConvenioParaMi
import mx.sigeh.app.core.model.Movimiento
import mx.sigeh.app.core.model.PaseSalidaAutorizado

/** MOV: convenios (CU-MOV-02/03/22) y pases de salida (CU-MOV-14/15/16/17). */
interface MovimientosRepository {
    fun observarMovimientosDelServicio(servicioId: String): Flow<List<Movimiento>>

    fun observarConveniosParaMi(): Flow<List<ConvenioParaMi>>

    /** Solo cuenta cuando la recibe el servidor: sin red queda como borrador. */
    suspend fun responderConvenio(movimientoId: String, aceptar: Boolean, enLinea: Boolean): RespuestaConvenio

    suspend fun paseAutorizadoHoy(programacionId: String): PaseSalidaAutorizado?

    /** Requiere conexión. El regreso es opcional y nunca se inventa la hora (RN-MOV-12). */
    suspend fun registrarSalida(
        programacionId: String,
        horaSalida: String,
        horaRegreso: String?,
        nota: String?,
    ): Boolean
}

enum class RespuestaConvenio { ENVIADA, GUARDADA_COMO_BORRADOR }
