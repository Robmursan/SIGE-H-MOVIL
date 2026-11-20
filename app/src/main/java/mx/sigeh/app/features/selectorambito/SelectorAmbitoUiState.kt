package mx.sigeh.app.features.selectorambito

data class OpcionServicio(
    val alcanceId: String,
    val nombre: String,
    val division: String,
    val rolTexto: String,
    val titular: Boolean,
    val seleccionado: Boolean,
)

data class OpcionTurno(
    val id: String,
    val nombre: String,
    val horario: String,
    val seleccionado: Boolean,
)

data class SelectorAmbitoUiState(
    val cargando: Boolean = true,
    val nombreUsuario: String = "",
    val hospitalNombre: String = "",
    val hospitalDetalle: String = "",
    val servicios: List<OpcionServicio> = emptyList(),
    val turnos: List<OpcionTurno> = emptyList(),
    val pendientesSinEnviar: Int = 0,
    val cerrandoSesion: Boolean = false,
    /** IAM no informó ninguna asignación vigente: no se ofrece ningún ámbito. */
    val sinAsignaciones: Boolean = false,
) {
    val puedeContinuar: Boolean get() = servicios.any { it.seleccionado } && turnos.any { it.seleccionado }
}

sealed interface SelectorAmbitoEvento {
    data object Continuar : SelectorAmbitoEvento
    data object SesionCerrada : SelectorAmbitoEvento
}
