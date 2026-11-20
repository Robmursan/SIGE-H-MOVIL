package mx.sigeh.app.core.data

import mx.sigeh.app.core.model.AcuseCaptura
import mx.sigeh.app.core.network.ApiError
import mx.sigeh.app.core.network.CausaErrorApi

/**
 * Resultado de la primera captura en línea (`PUT /programaciones/{id}/asistencia`, CU-ASI-02).
 *
 * La `Idempotency-Key` de la intención se descarta solo con un resultado [esTerminal]
 * (200, 400, 403, 404 o 409). Con [Incierta], [NoHabilitada] o [SesionTerminada] se conserva y el
 * siguiente intento de la misma jornada con el mismo estado la reutiliza.
 */
sealed interface ResultadoCaptura {

    /** Respuesta definitiva del servidor: ya no se reintenta con esa clave. */
    val esTerminal: Boolean get() = false

    /** 200: el servidor registró la captura (o devolvió el mismo acuse a un reintento). */
    data class Confirmada(val acuse: AcuseCaptura) : ResultadoCaptura {
        override val esTerminal: Boolean get() = true
    }

    /** 400 o 409: el servidor no aceptó la captura por [motivo]. */
    data class Rechazada(val motivo: MotivoRechazo) : ResultadoCaptura {
        override val esTerminal: Boolean get() = true
    }

    /** 403 «Acceso denegado»: sin facultad o ámbito vencido en este servicio. */
    data object SinPermiso : ResultadoCaptura {
        override val esTerminal: Boolean get() = true
    }

    /** 404: la jornada o el contexto ya no están disponibles. Se recarga la lista. */
    data object NoEncontrada : ResultadoCaptura {
        override val esTerminal: Boolean get() = true
    }

    /**
     * Timeout, falta de red, 503 transitorio u otra respuesta sin acuse: **puede que sí se haya
     * guardado**. Se ofrece «Reintentar» con la misma clave, que nunca duplica el registro.
     */
    data object Incierta : ResultadoCaptura

    /** 503 «Asistencia no habilitada»: el módulo está apagado en este servidor. Sin reintento automático. */
    data object NoHabilitada : ResultadoCaptura

    /** 401 después de intentar una renovación: hay que iniciar sesión de nuevo. */
    data object SesionTerminada : ResultadoCaptura
}

/** Motivos de rechazo con texto propio en la app (prompt M2 §6.6). */
enum class MotivoRechazo {
    /** 409: otra persona (u otro dispositivo) ya registró esta jornada. */
    YA_REGISTRADA,

    /** 409: fuera del horario de captura que calcula el servidor. */
    FUERA_VENTANA,

    /** 409: programación cancelada, sustituida o cerrada. */
    PROGRAMACION_NO_CAPTURABLE,

    /** 409: el contrato de la persona no está vigente. */
    CONTRATO_NO_VIGENTE,

    /** 409: el balance del periodo ya está cerrado. */
    CIERRE_EXISTENTE,

    /** 409 `IDEMPOTENCY_CONFLICT`: la clave se reutilizó con otro estado. Se descarta y se recarga. */
    CONFLICTO_IDEMPOTENCIA,

    /** 409 con un código que la app no conoce: se trata como «La jornada no admite captura». */
    NO_ADMITE_CAPTURA,

    /** 400: error de programación de la app. Se registra en debug y se muestra un mensaje genérico. */
    SOLICITUD_INVALIDA,
}

/** Traduce un error HTTP de la captura según la tabla del prompt M2 §6.6. */
fun ApiError.aResultadoCaptura(): ResultadoCaptura = when (httpCode) {
    400 -> ResultadoCaptura.Rechazada(MotivoRechazo.SOLICITUD_INVALIDA)
    401 -> ResultadoCaptura.SesionTerminada
    403 -> ResultadoCaptura.SinPermiso
    404 -> ResultadoCaptura.NoEncontrada
    409 -> ResultadoCaptura.Rechazada(
        when (causa) {
            CausaErrorApi.YA_REGISTRADA -> MotivoRechazo.YA_REGISTRADA
            CausaErrorApi.FUERA_VENTANA -> MotivoRechazo.FUERA_VENTANA
            CausaErrorApi.PROGRAMACION_NO_CAPTURABLE -> MotivoRechazo.PROGRAMACION_NO_CAPTURABLE
            CausaErrorApi.CONTRATO_NO_VIGENTE -> MotivoRechazo.CONTRATO_NO_VIGENTE
            CausaErrorApi.CIERRE_EXISTENTE -> MotivoRechazo.CIERRE_EXISTENTE
            CausaErrorApi.IDEMPOTENCY_CONFLICT -> MotivoRechazo.CONFLICTO_IDEMPOTENCIA
            else -> MotivoRechazo.NO_ADMITE_CAPTURA
        },
    )
    503 -> if (causa == CausaErrorApi.ASISTENCIA_NO_HABILITADA) ResultadoCaptura.NoHabilitada else ResultadoCaptura.Incierta
    // 429 (el PUT no lo declara) y 5xx: el servidor no confirmó nada; reintentar con la misma clave es seguro.
    else -> ResultadoCaptura.Incierta
}
