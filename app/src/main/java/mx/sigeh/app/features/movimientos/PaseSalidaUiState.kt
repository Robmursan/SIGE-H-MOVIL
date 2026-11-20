package mx.sigeh.app.features.movimientos

import mx.sigeh.app.core.model.PaseSalidaAutorizado

data class TrabajadorProgramado(
    val programacionId: String,
    val texto: String,
)

data class PaseSalidaUiState(
    val cargando: Boolean = true,
    val trabajadores: List<TrabajadorProgramado> = emptyList(),
    val seleccionado: TrabajadorProgramado? = null,
    val paseAutorizado: PaseSalidaAutorizado? = null,
    val horaSalida: String = "",
    val registrarRegreso: Boolean = false,
    val horaRegreso: String = "",
    val nota: String = "",
    val enLinea: Boolean = false,
    val guardando: Boolean = false,
    val puedeRegistrar: Boolean = true,
    val error: String? = null,
) {
    /** Registrar la salida efectiva requiere un pase autorizado y conexión (no es captura preliminar). */
    val puedeGuardar: Boolean
        get() = seleccionado != null && paseAutorizado != null && enLinea && !guardando && puedeRegistrar &&
            horaSalida.isNotBlank() && (!registrarRegreso || horaRegreso > horaSalida)
}

sealed interface PaseSalidaEvento {
    data object Registrada : PaseSalidaEvento
}

internal fun paseSalidaDeEjemplo(enLinea: Boolean = true, registrarRegreso: Boolean = false): PaseSalidaUiState {
    val daniela = TrabajadorProgramado("prog-0006", "Daniela Cruz V. · Auxiliar de enfermería B")
    return PaseSalidaUiState(
        cargando = false,
        trabajadores = listOf(
            daniela,
            TrabajadorProgramado("prog-0001", "Ana Rosales M. · Enf. general A"),
            TrabajadorProgramado("prog-0002", "Jorge Salas T. · Enf. especialista B"),
        ),
        seleccionado = daniela,
        paseAutorizado = PaseSalidaAutorizado("prog-0006", "11:30"),
        horaSalida = "11:32",
        registrarRegreso = registrarRegreso,
        horaRegreso = "13:10",
        enLinea = enLinea,
    )
}
