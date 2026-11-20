package mx.sigeh.app.features.agenda

import mx.sigeh.app.core.data.fake.DatosFicticios
import mx.sigeh.app.core.model.Agenda

data class AgendaUiState(
    val cargando: Boolean = true,
    val subtitulo: String = "",
    val agenda: Agenda? = null,
    val porEnviar: Int = 0,
)

internal fun agendaDeEjemplo() = AgendaUiState(
    cargando = false,
    subtitulo = "M. Hernández · Medicina Interna",
    agenda = DatosFicticios.agenda(DatosFicticios.copiaGuardadaMillis),
    porEnviar = 3,
)
