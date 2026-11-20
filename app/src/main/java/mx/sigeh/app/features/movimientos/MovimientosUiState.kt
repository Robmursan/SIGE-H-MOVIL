package mx.sigeh.app.features.movimientos

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.designsystem.theme.TonoEstado
import mx.sigeh.app.core.model.ConvenioParaMi
import mx.sigeh.app.core.model.EstadoMovimiento
import mx.sigeh.app.core.model.EstadoPaseSalida
import mx.sigeh.app.core.model.Movimiento
import mx.sigeh.app.core.model.TipoMovimiento

enum class FiltroMovimientos { DEL_SERVICIO, PARA_MI }

data class MovimientoUi(
    val id: String,
    val tipo: String,
    val titulo: String,
    val detalle: String,
    val estadoTexto: String,
    val tono: TonoEstado,
)

data class MovimientosUiState(
    val cargando: Boolean = true,
    val filtro: FiltroMovimientos = FiltroMovimientos.DEL_SERVICIO,
    val porEnviar: Int = 0,
    val enLinea: Boolean = false,
    val movimientos: List<MovimientoUi> = emptyList(),
    val convenios: List<ConvenioParaMi> = emptyList(),
    val respondiendo: String? = null,
    val puedeRegistrarSalida: Boolean = true,
    val mensaje: String? = null,
)

/** Texto y tono del estado de un movimiento, según su tipo (spec §6.2). */
fun Movimiento.aUi(): MovimientoUi {
    val (texto, tono) = when {
        tipo == TipoMovimiento.PASE_SALIDA -> when (estadoPase) {
            EstadoPaseSalida.AUTORIZADO -> "Autorizado" to TonoEstado.POSITIVO
            EstadoPaseSalida.SALIDA_REGISTRADA -> "Salida registrada" to TonoEstado.ADVERTENCIA
            EstadoPaseSalida.REGRESO_REGISTRADO -> "Regresó" to TonoEstado.POSITIVO
            EstadoPaseSalida.CERRADO_SIN_REGRESO -> "Cerrado sin regreso" to TonoEstado.NEUTRO
            EstadoPaseSalida.NO_EJECUTADO -> "No ejecutado" to TonoEstado.NEUTRO
            null -> "Sin estado" to TonoEstado.NEUTRO
        }
        else -> when (estado) {
            EstadoMovimiento.SOLICITADO ->
                if (tipo == TipoMovimiento.CONVENIO) "Espera al sustituto" to TonoEstado.ADVERTENCIA
                else "En revisión" to TonoEstado.INFORMATIVO
            EstadoMovimiento.ACEPTADO_POR_SUSTITUTO -> "Aceptado" to TonoEstado.POSITIVO
            EstadoMovimiento.AUTORIZADO -> "Autorizado" to TonoEstado.POSITIVO
            EstadoMovimiento.APLICADO -> "Aplicado" to TonoEstado.POSITIVO
            EstadoMovimiento.RECHAZADO -> "Rechazado" to TonoEstado.NEGATIVO
            EstadoMovimiento.REVOCADO -> "Revocado" to TonoEstado.NEUTRO
            null -> "Sin estado" to TonoEstado.NEUTRO
        }
    }
    return MovimientoUi(id, tipo.etiqueta, titulo, detalle, texto, tono)
}

fun EstadoMovimiento.etiquetaJefatura(): String = when (this) {
    EstadoMovimiento.AUTORIZADO, EstadoMovimiento.APLICADO -> "Autorizado"
    EstadoMovimiento.RECHAZADO -> "Rechazado"
    EstadoMovimiento.REVOCADO -> "Revocado"
    EstadoMovimiento.SOLICITADO, EstadoMovimiento.ACEPTADO_POR_SUSTITUTO -> "Pendiente"
}

internal fun movimientosDeEjemplo(filtro: FiltroMovimientos = FiltroMovimientos.DEL_SERVICIO) = MovimientosUiState(
    cargando = false,
    filtro = filtro,
    porEnviar = 3,
    movimientos = DatosFicticios.movimientosServicio.map { it.aUi() },
    convenios = DatosFicticios.conveniosParaMi,
)
