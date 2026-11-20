package mx.sigeh.app.features.incidencias

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.JornadaProgramada
import mx.sigeh.app.core.model.TipoIncidencia

data class RegistrarIncidenciaUiState(
    val cargando: Boolean = true,
    val noEncontrada: Boolean = false,
    val jornada: JornadaProgramada? = null,
    val contextoJornada: String = "",
    val tipos: List<TipoIncidencia> = emptyList(),
    val tipoSeleccionadoId: String? = null,
    val jornadaCompleta: Boolean = true,
    val desde: String = "07:00",
    val hasta: String = "08:00",
    val observacion: String = "",
    val guardando: Boolean = false,
    val puedeRegistrar: Boolean = true,
) {
    val tipoSeleccionado: TipoIncidencia? get() = tipos.firstOrNull { it.id == tipoSeleccionadoId }

    /** Si el tipo no admite periodos parciales, se fuerza la jornada completa. */
    val periodoEditable: Boolean get() = tipoSeleccionado?.permiteParcial != false

    val errorHorario: String?
        get() = if (!jornadaCompleta && hasta <= desde) "La hora final debe ser posterior a la inicial." else null

    val puedeGuardar: Boolean
        get() = jornada != null && tipoSeleccionado != null && errorHorario == null && !guardando && puedeRegistrar
}

sealed interface RegistrarIncidenciaEvento {
    data object Guardada : RegistrarIncidenciaEvento
}

/** Estado del mockup: Paola Gutiérrez L., Retardo, parte de la jornada 07:00–08:15. */
internal fun registrarIncidenciaDeEjemplo() = RegistrarIncidenciaUiState(
    cargando = false,
    jornada = DatosFicticios.jornadas.first { it.programacionId == "prog-0005" },
    contextoJornada = "Sáb 14 nov · Matutino 07:00 – 14:30 · Medicina Interna",
    tipos = DatosFicticios.tiposIncidencia,
    tipoSeleccionadoId = "inc-ret",
    jornadaCompleta = false,
    desde = "07:00",
    hasta = "08:15",
)
