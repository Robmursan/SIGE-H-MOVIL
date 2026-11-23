package mx.sigeh.app.core.model

import kotlinx.serialization.Serializable

/** Estados de asistencia de una jornada (spec §6.2). PENDIENTE nunca se convierte en AUSENTE por omisión. */
@Serializable
enum class EstadoAsistencia { PENDIENTE, PRESENTE, AUSENTE, PARCIAL, NO_EXIGIBLE }

/*
 * Vocabulario del contrato `sigeh-v1` (SHA-256 a6fbe480…, docs/mobile/contrato/sigeh-v1.json; sin cambios desde ab9dd2a4…).
 * Los nombres de los valores son los códigos exactos del servidor: no renombrar.
 * `EstadoAsistencia` (arriba) coincide con el `estadoOperativo` de `PaseListaItemDto`.
 */

/** Estado con el que se captura una jornada (`CapturarAsistenciaDto.estado`). Nunca hay uno por defecto. */
@Serializable
enum class EstadoCaptura {
    PRESENTE,
    AUSENTE,
    PARCIAL,
    ;

    fun aEstadoAsistencia(): EstadoAsistencia = when (this) {
        PRESENTE -> EstadoAsistencia.PRESENTE
        AUSENTE -> EstadoAsistencia.AUSENTE
        PARCIAL -> EstadoAsistencia.PARCIAL
    }
}

/** Estado de la programación de la jornada (`PaseListaItemDto.estadoProgramacion`). */
@Serializable
enum class EstadoProgramacion { PROGRAMADA, CANCELADA, SUSTITUIDA, CERRADA }

/** Validación administrativa de una captura. "Ausente" no es falta hasta que Coordinación la valida. */
@Serializable
enum class ValidacionAdministrativa { PENDIENTE, VALIDADA, OBSERVADA }

/** Por qué el servidor no permite capturar una jornada. La app no lo calcula: lo lee del item. */
@Serializable
enum class MotivoNoCapturable {
    YA_REGISTRADA,
    FUERA_VENTANA,
    PROGRAMACION_NO_CAPTURABLE,
    CONTRATO_NO_VIGENTE,
    CIERRE_EXISTENTE,
    SIN_FACULTAD_CAPTURA,
}

/**
 * Acuse del servidor tras la primera captura (`CaptureReceiptDto`).
 * [eventoId] y [revision] son BIGINT decimales: siempre texto, nunca `Long`.
 * [registradoEn] es ISO-8601 con offset, en la hora del servidor.
 */
data class AcuseCaptura(
    val programacionId: String,
    val asistenciaId: String,
    val eventoId: String,
    val estado: EstadoCaptura,
    val validacion: ValidacionAdministrativa,
    val actorId: String,
    val registradoEn: String,
    val revision: String,
)

/**
 * Página del pase de lista en línea (`PaseListaPageDto`). Todo conteo que se haga con
 * [jornadas] es «en esta página», nunca un total del servicio.
 */
data class PaginaPaseLista(
    val hospitalId: String,
    val servicioId: String,
    /** Fecha local consultada, `YYYY-MM-DD`. */
    val fecha: String,
    /** Zona IANA del hospital: en ella se calcula «hoy» y se muestran las horas. */
    val zonaHoraria: String,
    val jornadas: List<JornadaPase>,
    /** Cursor para «Cargar más» con los mismos filtros; null si es la última página. */
    val siguienteCursor: String?,
)

/**
 * Jornada del pase en línea (`PaseListaItemDto`). La app no calcula si se puede capturar:
 * usa [capturable] y [motivoNoCapturable] tal como los envía el servidor.
 */
data class JornadaPase(
    val programacionId: String,
    val empleadoId: String,
    /** `nombres + " " + primerApellido` (el contrato no trae segundo apellido). */
    val nombre: String,
    val horarioId: String?,
    val turnoCodigo: String?,
    /** ISO-8601 con offset. */
    val inicioProgramado: String,
    /** ISO-8601 con offset. */
    val finProgramado: String,
    val estadoProgramacion: EstadoProgramacion,
    val capturada: Boolean,
    val asistenciaId: String?,
    val estadoOperativo: EstadoAsistencia,
    val validacion: ValidacionAdministrativa?,
    /** BIGINT decimal. */
    val revision: String,
    val ultimaCaptura: UltimaCaptura?,
    val capturable: Boolean,
    val motivoNoCapturable: MotivoNoCapturable?,
)

/** Quién y cuándo capturó. [actorId] solo se compara con el usuario de la sesión («Tú» / «Otro usuario»). */
data class UltimaCaptura(
    /** BIGINT decimal. */
    val eventoId: String,
    val actorId: String,
    /** ISO-8601 con offset, hora del servidor. */
    val registradoEn: String,
)

/** Estados del pase de lista (spec §6.2). */
enum class EstadoPaseLista { BORRADOR, ENVIADO, VALIDADO, REABIERTO }

enum class TonoAviso { NEUTRO, INFORMATIVO, ADVERTENCIA }

data class AvisoJornada(
    val texto: String,
    val tono: TonoAviso,
)

/**
 * Jornada programada (tabla `programacion_laboral`). Su identificador es el `programacionId`
 * que usan los comandos locales y CU-ASI-02 (`PUT /programaciones/:id/asistencia`).
 * Del RUD solo se guarda y muestra el final truncado.
 */
data class JornadaProgramada(
    val programacionId: String,
    val empleadoId: String,
    val nombre: String,
    val categoria: String,
    val horario: String,
    val rudUltimos4: String,
    val exigible: Boolean,
    val motivoNoExigible: String? = null,
    val aviso: AvisoJornada? = null,
) {
    val nombreCorto: String get() = abreviarNombre(nombre)
}

/** Lo que el servidor tiene registrado para una jornada (puede estar validado por Supervisión). */
data class RegistroAsistenciaServidor(
    val programacionId: String,
    val estado: EstadoAsistencia,
    val validado: Boolean,
    val validadoPor: String? = null,
    val horaValidacion: String? = null,
)

data class PaseLista(
    val id: String,
    val programacionVersion: Int,
    val fechaCorta: String,
    val estado: EstadoPaseLista,
    val jornadas: List<JornadaProgramada>,
    val registros: Map<String, RegistroAsistenciaServidor>,
    val envioSolicitadoMillis: Long?,
)

/** "Paola Gutiérrez L." → "P. Gutiérrez". */
fun abreviarNombre(nombre: String): String {
    val partes = nombre.trim().split(" ").filter { it.isNotBlank() }
    return if (partes.size >= 2) "${partes[0].first()}. ${partes[1]}" else nombre.trim()
}

fun EstadoAsistencia.etiqueta(): String = when (this) {
    EstadoAsistencia.PENDIENTE -> "Sin capturar"
    EstadoAsistencia.PRESENTE -> "Presente"
    EstadoAsistencia.AUSENTE -> "Ausente"
    EstadoAsistencia.PARCIAL -> "Parcial"
    EstadoAsistencia.NO_EXIGIBLE -> "No exigible"
}
