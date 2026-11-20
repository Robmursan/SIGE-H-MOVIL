package mx.sigeh.app.core.network

import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException

/**
 * Error de la API. Se aceptan dos formatos:
 * - **Real hoy en SIGE-H-WEB** (sin filtro global): el de NestJS, `{statusCode, message, error}`
 *   (`ReadHttpErrorDto`), donde `message` puede ser texto o lista (errores de validación). El 429 de
 *   IAM es `{statusCode, message}`, sin `error`.
 * - **Especificación §8** (aún no implementado en el backend): `{code, message, details, traceId, retryable}`.
 * Los mensajes del servidor son genéricos y no se muestran tal cual a la persona usuaria: la app
 * decide qué decir con [tipo] y, cuando el mensaje es uno de los conocidos, con [causa].
 */
data class ApiError(
    val httpCode: Int,
    val tipo: TipoErrorApi,
    val code: String?,
    val message: String?,
    val traceId: String?,
    val retryable: Boolean,
    /** Causa concreta si `message` es uno de los textos reales del backend; null si no se reconoce. */
    val causa: CausaErrorApi? = null,
    /**
     * Segundos de espera de `Retry-After`. En un 429 sin encabezado vale
     * [ApiErrorParser.ESPERA_POR_DEFECTO_S]; en el resto de códigos solo existe si el servidor lo envía.
     */
    val reintentarEnSegundos: Long? = null,
)

enum class TipoErrorApi {
    SOLICITUD_INVALIDA, // 400
    NO_AUTENTICADO, // 401: sesión vencida, detener la cola
    SIN_FACULTAD, // 403: ámbito o permiso vencido, detener la cola
    NO_ENCONTRADO, // 404
    CONFLICTO, // 409
    VALIDACION, // 422
    LIMITE, // 429: límite persistente de intentos
    NO_DISPONIBLE, // 503: IAM deshabilitado o persistencia no disponible
    OTRO,
}

/**
 * Textos exactos que envía SIGE-H-WEB (main 2d1ec62) en `message`, por código HTTP. En los 409 de
 * asistencia el `message` es el código de negocio (`YA_REGISTRADA`, `IDEMPOTENCY_CONFLICT`…).
 * Si el backend cambia un texto, la causa queda en null y la app cae al manejo genérico de [TipoErrorApi].
 */
enum class CausaErrorApi(
    val httpCode: Int,
    val mensaje: String,
    /** Si un reintento automático (con la misma clave) tiene sentido. */
    val reintentable: Boolean = false,
) {
    // 400: errores de programación; al usuario solo se le muestra un mensaje genérico.
    ENTRADA_INVALIDA(400, "Entrada inválida"),
    FECHA_INVALIDA(400, "Fecha inválida"),
    LIMITE_INVALIDO(400, "Límite inválido"),
    IDENTIFICADOR_O_CLAVE_INVALIDOS(400, "Identificador o clave inválidos"),

    // 403
    ACCESO_DENEGADO(403, "Acceso denegado"),

    // 404
    CONTEXTO_NO_DISPONIBLE(404, "Contexto no disponible"),
    PROGRAMACION_NO_DISPONIBLE(404, "Programación no disponible"),

    // 409: `PUT /programaciones/{id}/asistencia`
    YA_REGISTRADA(409, "YA_REGISTRADA"),
    FUERA_VENTANA(409, "FUERA_VENTANA"),
    PROGRAMACION_NO_CAPTURABLE(409, "PROGRAMACION_NO_CAPTURABLE"),
    CONTRATO_NO_VIGENTE(409, "CONTRATO_NO_VIGENTE"),
    CIERRE_EXISTENTE(409, "CIERRE_EXISTENTE"),

    /** La misma `Idempotency-Key` se usó con otro estado: error interno de la app. */
    IDEMPOTENCY_CONFLICT(409, "IDEMPOTENCY_CONFLICT"),

    // 503
    /** `ASISTENCIA_ENABLED=false`: no se reintenta solo. */
    ASISTENCIA_NO_HABILITADA(503, "Asistencia no habilitada"),

    /** Resultado transaccional incierto: puede que sí se haya guardado; se reintenta con la misma clave. */
    ASISTENCIA_INCIERTA(503, "Asistencia no disponible; reintente con la misma clave", reintentable = true),

    /** IAM deshabilitado o su persistencia caída: el backend usa el mismo texto para ambos casos. */
    IAM_NO_DISPONIBLE(503, "IAM no disponible", reintentable = true),
    ;

    companion object {
        fun de(httpCode: Int, mensaje: String?): CausaErrorApi? =
            mensaje?.trim()?.let { texto -> entries.firstOrNull { it.httpCode == httpCode && it.mensaje == texto } }
    }
}

class ApiErrorParser @Inject constructor(
    private val json: Json,
) {
    /**
     * @param retryAfter valor crudo del encabezado `Retry-After`, si vino. Solo se aceptan segundos
     *   (`delta-seconds`); una fecha HTTP se ignora para no depender del reloj del teléfono.
     */
    fun parse(httpCode: Int, cuerpo: String?, retryAfter: String? = null): ApiError {
        val objeto: JsonObject? = cuerpo
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val tipo = tipoPara(httpCode)
        val mensaje = objeto?.mensaje()
        val causa = CausaErrorApi.de(httpCode, mensaje)
        val reintentableSegunServidor = (objeto?.get("retryable") as? JsonPrimitive)?.booleanOrNull
        return ApiError(
            httpCode = httpCode,
            tipo = tipo,
            code = objeto?.texto("code") ?: objeto?.texto("error"),
            message = mensaje,
            traceId = objeto?.texto("traceId"),
            retryable = reintentableSegunServidor ?: causa?.reintentable ?: (tipo in REINTENTABLES),
            causa = causa,
            reintentarEnSegundos = segundosDeEspera(retryAfter) ?: ESPERA_POR_DEFECTO_S.takeIf { httpCode == 429 },
        )
    }

    /** Lee código, cuerpo y `Retry-After` de un error HTTP de Retrofit. El cuerpo solo se puede leer una vez. */
    fun parse(error: HttpException): ApiError {
        val respuesta = error.response()
        val cuerpo = try {
            respuesta?.errorBody()?.string()
        } catch (_: IOException) {
            null
        }
        return parse(error.code(), cuerpo, respuesta?.headers()?.get(ENCABEZADO_RETRY_AFTER))
    }

    private fun JsonObject.texto(campo: String): String? = (this[campo] as? JsonPrimitive)?.contentOrNull

    /** NestJS envía `message` como texto o como lista de errores de validación. */
    private fun JsonObject.mensaje(): String? = when (val valor = this["message"]) {
        is JsonPrimitive -> valor.contentOrNull
        is JsonArray -> valor.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("; ").ifEmpty { null }
        else -> null
    }

    private fun segundosDeEspera(retryAfter: String?): Long? =
        retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

    companion object {
        const val ENCABEZADO_RETRY_AFTER = "Retry-After"

        /** Espera de un 429 cuando el servidor no envía `Retry-After`. */
        const val ESPERA_POR_DEFECTO_S = 60L

        private val REINTENTABLES = setOf(TipoErrorApi.LIMITE, TipoErrorApi.NO_DISPONIBLE)

        fun tipoPara(httpCode: Int): TipoErrorApi = when (httpCode) {
            400 -> TipoErrorApi.SOLICITUD_INVALIDA
            401 -> TipoErrorApi.NO_AUTENTICADO
            403 -> TipoErrorApi.SIN_FACULTAD
            404 -> TipoErrorApi.NO_ENCONTRADO
            409 -> TipoErrorApi.CONFLICTO
            422 -> TipoErrorApi.VALIDACION
            429 -> TipoErrorApi.LIMITE
            503 -> TipoErrorApi.NO_DISPONIBLE
            else -> TipoErrorApi.OTRO
        }
    }
}
