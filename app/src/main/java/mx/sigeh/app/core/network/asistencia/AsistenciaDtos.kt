package mx.sigeh.app.core.network.asistencia

import kotlinx.serialization.Serializable
import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.model.EstadoAsistencia
import mx.sigeh.app.core.model.EstadoCaptura
import mx.sigeh.app.core.model.EstadoProgramacion
import mx.sigeh.app.core.model.JornadaPase
import mx.sigeh.app.core.model.MotivoNoCapturable
import mx.sigeh.app.core.model.PaginaPaseLista
import mx.sigeh.app.core.model.UltimaCaptura
import mx.sigeh.app.core.model.ValidacionAdministrativa

/*
 * DTO de asistencia operativa copiados del contrato `sigeh-v1` (docs/mobile/contrato/sigeh-v1.json,
 * SHA-256 ab9dd2a4b17806784c029ca16b79a4837542f83ac3ea3a0246cb6b50a18847f0), que corresponde a
 * SIGE-H-WEB `apps/api/src/modules/asistencia/asistencia.dto.ts` (main, commit 2d1ec62).
 *
 * - Los identificadores grandes (`eventoId`, `revision`) son BIGINT decimales: van como `String`.
 * - Las fechas y horas son ISO-8601 con offset y se dejan como texto (minSdk 24 sin java.time).
 * - Los campos `nullable` del contrato llevan `= null`: se aceptan tanto `null` como el campo ausente.
 * Si el contrato cambia, actualizar aquí y en docs/mobile/contrato.md.
 */

/** Respuesta de `GET /pases-lista` (`PaseListaPageDto`). Una página vacía es `items = []`. */
@Serializable
data class PaseListaPageDto(
    val hospitalId: String,
    val servicioId: String,
    /** Fecha local consultada, `YYYY-MM-DD`. */
    val fecha: String,
    /** Zona IANA del hospital (por ejemplo `America/Mexico_City`). */
    val zonaHoraria: String,
    val items: List<PaseListaItemDto>,
    /** Cursor de la siguiente página; null si es la última. */
    val nextCursor: String? = null,
    val limit: Int,
    /** Siempre `id:asc`. */
    val order: String,
) {
    fun aPagina() = PaginaPaseLista(
        hospitalId = hospitalId,
        servicioId = servicioId,
        fecha = fecha,
        zonaHoraria = zonaHoraria,
        jornadas = items.map { it.aJornada() },
        siguienteCursor = nextCursor,
    )
}

/** Una jornada del pase (`PaseListaItemDto`). La capturabilidad la decide el servidor. */
@Serializable
data class PaseListaItemDto(
    val programacionId: String,
    val empleado: AttendanceEmployeeDto,
    val horarioId: String? = null,
    val turnoCodigo: String? = null,
    /** date-time con offset. */
    val inicioProgramado: String,
    /** date-time con offset. */
    val finProgramado: String,
    val estadoProgramacion: EstadoProgramacion,
    val capturada: Boolean,
    val asistenciaId: String? = null,
    val estadoOperativo: EstadoAsistencia,
    val validacionAdministrativa: ValidacionAdministrativa? = null,
    /** BIGINT decimal; `"0"` mientras no haya eventos. */
    val revision: String,
    val ultimaCaptura: LastCaptureDto? = null,
    val capturable: Boolean,
    val motivoNoCapturable: MotivoNoCapturable? = null,
) {
    fun aJornada() = JornadaPase(
        programacionId = programacionId,
        empleadoId = empleado.empleadoId,
        nombre = empleado.nombreVisible,
        horarioId = horarioId,
        turnoCodigo = turnoCodigo,
        inicioProgramado = inicioProgramado,
        finProgramado = finProgramado,
        estadoProgramacion = estadoProgramacion,
        capturada = capturada,
        asistenciaId = asistenciaId,
        estadoOperativo = estadoOperativo,
        validacion = validacionAdministrativa,
        revision = revision,
        ultimaCaptura = ultimaCaptura?.let { UltimaCaptura(it.eventoId, it.actorId, it.registradoEn) },
        capturable = capturable,
        motivoNoCapturable = motivoNoCapturable,
    )
}

/** El contrato no trae segundo apellido: el nombre visible es `nombres + " " + primerApellido`. */
@Serializable
data class AttendanceEmployeeDto(
    val empleadoId: String,
    val nombres: String,
    val primerApellido: String,
) {
    val nombreVisible: String get() = "$nombres $primerApellido".trim()
}

/** Última captura registrada. [actorId] se compara con `ProfileDto.usuarioId`; nunca se muestra. */
@Serializable
data class LastCaptureDto(
    /** BIGINT decimal. */
    val eventoId: String,
    val actorId: String,
    /** date-time con offset (hora del servidor). */
    val registradoEn: String,
)

/**
 * Cuerpo de `PUT /programaciones/{programacionId}/asistencia`. Solo primera captura:
 * `expectedRevision` es siempre `"0"`. Se construye con [primeraCaptura] para que nadie envíe otra revisión.
 */
@Serializable
data class CapturarAsistenciaDto(
    val estado: EstadoCaptura,
    val expectedRevision: String,
) {
    companion object {
        const val REVISION_PRIMERA_CAPTURA = "0"

        fun primeraCaptura(estado: EstadoCaptura) = CapturarAsistenciaDto(estado, REVISION_PRIMERA_CAPTURA)
    }
}

/** Acuse de `PUT …/asistencia` (`CaptureReceiptDto`). Un reintento con la misma clave devuelve el mismo. */
@Serializable
data class CaptureReceiptDto(
    val programacionId: String,
    val asistenciaId: String,
    /** BIGINT decimal: no convertir a número. */
    val eventoId: String,
    val estadoOperativo: EstadoCaptura,
    /** Siempre `PENDIENTE` en la primera captura. */
    val validacionAdministrativa: ValidacionAdministrativa,
    val actorId: String,
    /** date-time con offset (hora del servidor). */
    val registradoEn: String,
    /** BIGINT decimal. */
    val revision: String,
) {
    fun aAcuse() = AcuseCaptura(
        programacionId = programacionId,
        asistenciaId = asistenciaId,
        eventoId = eventoId,
        estado = estadoOperativo,
        validacion = validacionAdministrativa,
        actorId = actorId,
        registradoEn = registradoEn,
        revision = revision,
    )
}
