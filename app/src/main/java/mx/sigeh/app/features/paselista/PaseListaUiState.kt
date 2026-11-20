package mx.sigeh.app.features.paselista

import kotlinx.serialization.Serializable
import mx.sigeh.app.core.common.HoraHospital
import mx.sigeh.app.core.designsystem.components.AcuseSnackbarVisuals
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.MotivoNoCapturable
import mx.sigeh.app.core.model.ValidacionAdministrativa

/** Cómo se dibuja una fila (prompt M2 §9.3.4). */
enum class TipoFila {
    /** `capturable`: badge «Por capturar» y botones. */
    CAPTURABLE,

    /** `capturada`: badge del estado y quién capturó. Se toca para abrir el detalle. */
    CAPTURADA,

    /** Ni capturable ni capturada: badge neutro y el motivo, sin botones. */
    NO_CAPTURABLE,

    /** Hay un envío en curso: «Enviando…», sin botones. */
    ENVIANDO,
}

data class FilaJornada(
    val programacionId: String,
    val nombre: String,
    /** «TM · 07:00–14:30». */
    val detalle: String,
    val tipo: TipoFila,
    val estadoOperativo: EstadoAsistencia,
    val estadoProgramacion: EstadoProgramacion,
    /** «Capturado por Tú · 07:04 · validación pendiente». */
    val lineaCaptura: String?,
    val motivo: String?,
    /** Recién confirmada: se resalta unos segundos. */
    val resaltada: Boolean,
)

/** Chip de turno derivado de los items cargados (no hay catálogo de horarios en el backend). */
data class ChipTurno(val horarioId: String, val turnoCodigo: String)

/** Estados que impiden mostrar el pase (prompt M2 §9.10). */
enum class BloqueoPase { SIN_PERMISO, CONTEXTO_NO_DISPONIBLE, NO_HABILITADO, SESION_TERMINADA }

/** Confirmación abierta para una jornada, con el estado elegido en la hoja. */
data class HojaCaptura(
    val programacionId: String,
    val nombre: String,
    val detalle: String,
    val estado: EstadoCaptura,
    val enviando: Boolean,
)

/** Diálogos de error de captura (prompt M2 §9.6). */
sealed interface DialogoCaptura {
    /** Timeout, red o 503 transitorio: «Reintentar» usa la misma clave. */
    data class Incierta(val programacionId: String, val estado: EstadoCaptura) : DialogoCaptura

    /** «Esta jornada ya tenía registro», con «Ver registro». */
    data class YaRegistrada(val programacionId: String) : DialogoCaptura

    data object FueraDeHorario : DialogoCaptura

    data class NoAdmiteCaptura(val motivo: String) : DialogoCaptura

    data object NoDisponible : DialogoCaptura

    data object NoHabilitada : DialogoCaptura

    /** 400 o `IDEMPOTENCY_CONFLICT`: error de la app; se recarga la lista. */
    data object ErrorInterno : DialogoCaptura
}

data class PaseListaUiState(
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val cargandoMas: Boolean = false,
    val servicio: String = "",
    /** «Hospital A · America/Mexico_City». */
    val subtitulo: String = "",
    val fecha: String = "",
    val fechaTexto: String = "",
    val esHoy: Boolean = true,
    val turnos: List<ChipTurno> = emptyList(),
    val horarioSeleccionado: String? = null,
    val filas: List<FilaJornada> = emptyList(),
    val capturadas: Int = 0,
    val capturables: Int = 0,
    val conteos: Map<EstadoAsistencia, Int> = emptyMap(),
    val hayMas: Boolean = false,
    val enLinea: Boolean = true,
    val errorCarga: Boolean = false,
    val bloqueo: BloqueoPase? = null,
    val hoja: HojaCaptura? = null,
    val dialogo: DialogoCaptura? = null,
    /** Sin servicio elegido (por ejemplo, tras restaurar el proceso): hay que volver al selector. */
    val sinServicio: Boolean = false,
) {
    val vacio: Boolean get() = !cargando && !errorCarga && bloqueo == null && filas.isEmpty()
}

sealed interface PaseListaEvento {
    /** Tras el 200: snackbar con «Ver acuse». */
    data class MostrarAcuse(val visuales: AcuseSnackbarVisuals, val programacionId: String) : PaseListaEvento
}

/** Intención de captura sin respuesta terminal; vive en `SavedStateHandle` (prompt M2 §5.5). */
@Serializable
data class IntencionPendiente(
    val programacionId: String,
    val estado: EstadoCaptura,
    val clave: String,
)

// --- Textos (prompt M2 §6.6 y §9.3) -------------------------------------------------------------

fun ValidacionAdministrativa.texto(): String = when (this) {
    ValidacionAdministrativa.PENDIENTE -> "pendiente"
    ValidacionAdministrativa.VALIDADA -> "validada"
    ValidacionAdministrativa.OBSERVADA -> "observada"
}

/** «Tú» si la capturó la persona de la sesión; nunca se muestra un UUID. */
fun autorCaptura(actorId: String, usuarioId: String?): String = if (actorId == usuarioId) "Tú" else "Otro usuario"

/** «Capturado por Tú · 07:04 · validación pendiente». */
fun lineaCaptura(jornada: JornadaPase, usuarioId: String?, zona: String): String? {
    val captura = jornada.ultimaCaptura ?: return null
    val partes = mutableListOf("Capturado por ${autorCaptura(captura.actorId, usuarioId)}", HoraHospital.hora(captura.registradoEn, zona))
    jornada.validacion?.let { partes += "validación ${it.texto()}" }
    return partes.joinToString(" · ")
}

/**
 * Texto del motivo para una jornada no capturable. Con `FUERA_VENTANA` y la jornada aún sin
 * iniciar se dice desde qué hora; esto es solo informativo: quien decide es el servidor.
 */
fun textoMotivo(jornada: JornadaPase, zona: String, ahoraMillis: Long): String? = when (jornada.motivoNoCapturable) {
    null, MotivoNoCapturable.YA_REGISTRADA -> null
    MotivoNoCapturable.FUERA_VENTANA -> {
        val inicio = HoraHospital.instante(jornada.inicioProgramado)
        if (inicio != null && ahoraMillis < inicio) {
            "Se habilita a las ${HoraHospital.hora(inicio, zona)}"
        } else {
            "Fuera del horario de captura"
        }
    }
    MotivoNoCapturable.PROGRAMACION_NO_CAPTURABLE -> when (jornada.estadoProgramacion) {
        EstadoProgramacion.CANCELADA -> "No capturable: programación cancelada"
        EstadoProgramacion.SUSTITUIDA -> "No capturable: programación sustituida"
        EstadoProgramacion.CERRADA -> "No capturable: programación cerrada"
        EstadoProgramacion.PROGRAMADA -> "No capturable: programación no disponible para captura"
    }
    MotivoNoCapturable.CONTRATO_NO_VIGENTE -> "No capturable: contrato no vigente"
    MotivoNoCapturable.CIERRE_EXISTENTE -> "No capturable: el balance del periodo ya está cerrado"
    MotivoNoCapturable.SIN_FACULTAD_CAPTURA -> "Solo consulta: no tienes facultad de captura"
}

/** «TM · 07:00–14:30» (sin turno: solo el horario). */
fun detalleJornada(jornada: JornadaPase, zona: String): String =
    listOfNotNull(jornada.turnoCodigo, HoraHospital.intervalo(jornada.inicioProgramado, jornada.finProgramado, zona))
        .joinToString(" · ")

/** Chips «Todos» + uno por cada `turnoCodigo` con horario, en orden de aparición. */
fun turnosDe(jornadas: List<JornadaPase>): List<ChipTurno> = jornadas
    .mapNotNull { j -> j.horarioId?.let { ChipTurno(it, j.turnoCodigo ?: "Turno") } }
    .distinctBy { it.turnoCodigo }
