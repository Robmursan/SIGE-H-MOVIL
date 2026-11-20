package mx.sigeh.app.core.model

enum class TipoDiaAgenda { LABORAL, DESCANSO, FESTIVO }

data class DiaSemana(
    val dia: String,
    val numero: Int,
    val clave: String,
    val tipo: TipoDiaAgenda,
    val esHoy: Boolean,
)

data class ProximoDia(
    val fecha: String,
    val detalle: String,
    val etiqueta: String,
    val tipo: TipoDiaAgenda,
)

/** Agenda propia (CU-CAL-19) a partir de la copia de la programación guardada en el teléfono. */
data class Agenda(
    val hoyEtiqueta: String,
    val horario: String,
    val turnoServicio: String,
    val programacionVersion: Int,
    val descansoSemanal: String,
    val semanaTitulo: String,
    val semana: List<DiaSemana>,
    val leyenda: String,
    val proximos: List<ProximoDia>,
    val copiaGuardadaMillis: Long?,
)
