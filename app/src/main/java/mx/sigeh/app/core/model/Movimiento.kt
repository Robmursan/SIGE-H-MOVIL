package mx.sigeh.app.core.model

enum class TipoMovimiento(val etiqueta: String) {
    CONVENIO("CONVENIO"),
    CAMBIO_DESCANSO("CAMBIO DE DESCANSO"),
    PASE_SALIDA("PASE DE SALIDA"),
}

/** Estados de un movimiento laboral (spec §6.2). */
enum class EstadoMovimiento { SOLICITADO, ACEPTADO_POR_SUSTITUTO, AUTORIZADO, RECHAZADO, APLICADO, REVOCADO }

/** Estados de un pase de salida (spec §6.2). El regreso es opcional (RN-MOV-12). */
enum class EstadoPaseSalida { AUTORIZADO, SALIDA_REGISTRADA, REGRESO_REGISTRADO, CERRADO_SIN_REGRESO, NO_EJECUTADO }

/**
 * Movimiento del servicio. Los pases de salida usan [estadoPase]; el resto, [estado].
 */
data class Movimiento(
    val id: String,
    val tipo: TipoMovimiento,
    val titulo: String,
    val detalle: String,
    val estado: EstadoMovimiento? = null,
    val estadoPase: EstadoPaseSalida? = null,
)

/** Respuesta que el sustituto dejó guardada sin conexión. Solo cuenta cuando la recibe el servidor. */
enum class RespuestaBorrador { ACEPTAR, RECHAZAR }

/** Convenio de sustitución en el que la persona usuaria es la sustituta (CU-MOV-22). */
data class ConvenioParaMi(
    val movimientoId: String,
    val solicitante: String,
    val fecha: String,
    val turno: String,
    val servicio: String,
    val tipoConvenio: String,
    val estadoJefatura: EstadoMovimiento,
    val respuestaBorrador: RespuestaBorrador? = null,
)

data class PaseSalidaAutorizado(
    val programacionId: String,
    val horaAutorizada: String,
)
