package mx.sigeh.app.core.network

import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Error de la API. Se aceptan dos formatos:
 * - **Real hoy en SIGE-H-WEB** (sin filtro global): el de NestJS, `{statusCode, message, error}`,
 *   donde `message` puede ser texto o lista (errores de validación).
 * - **Especificación §8** (aún no implementado en el backend): `{code, message, details, traceId, retryable}`.
 * Los mensajes del servidor son genéricos y no se muestran tal cual a la persona usuaria.
 */
data class ApiError(
    val httpCode: Int,
    val tipo: TipoErrorApi,
    val code: String?,
    val message: String?,
    val traceId: String?,
    val retryable: Boolean,
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

class ApiErrorParser @Inject constructor(
    private val json: Json,
) {
    fun parse(httpCode: Int, cuerpo: String?): ApiError {
        val objeto: JsonObject? = cuerpo
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val tipo = tipoPara(httpCode)
        return ApiError(
            httpCode = httpCode,
            tipo = tipo,
            code = objeto?.texto("code") ?: objeto?.texto("error"),
            message = objeto?.mensaje(),
            traceId = objeto?.texto("traceId"),
            retryable = (objeto?.get("retryable") as? JsonPrimitive)?.booleanOrNull ?: (tipo in REINTENTABLES),
        )
    }

    private fun JsonObject.texto(campo: String): String? = (this[campo] as? JsonPrimitive)?.contentOrNull

    /** NestJS envía `message` como texto o como lista de errores de validación. */
    private fun JsonObject.mensaje(): String? = when (val valor = this["message"]) {
        is JsonPrimitive -> valor.contentOrNull
        is JsonArray -> valor.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("; ").ifEmpty { null }
        else -> null
    }

    companion object {
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
