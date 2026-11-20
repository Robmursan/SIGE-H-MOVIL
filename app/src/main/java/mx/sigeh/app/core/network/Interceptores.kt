package mx.sigeh.app.core.network

import java.util.UUID
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Agrega `Authorization: Bearer` con el token de la sesión cifrada. Si el token vence pronto lo
 * renueva antes de enviar (ver [TokenRefresher]). Las rutas públicas (login, refresh, salud) van sin token.
 */
class AuthInterceptor @Inject constructor(
    private val tokenRefresher: TokenRefresher,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()
        if (peticion.esPublica() || peticion.header(ENCABEZADO_AUTORIZACION) != null) return chain.proceed(peticion)
        val token = tokenRefresher.tokenVigente() ?: return chain.proceed(peticion)
        return chain.proceed(peticion.newBuilder().header(ENCABEZADO_AUTORIZACION, PREFIJO_BEARER + token).build())
    }
}

/** Agrega `X-Request-Id` para trazar cada petición con el `traceId` de los errores del servidor. */
class RequestIdInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()
        if (peticion.header(ENCABEZADO) != null) return chain.proceed(peticion)
        return chain.proceed(peticion.newBuilder().header(ENCABEZADO, UUID.randomUUID().toString()).build())
    }

    companion object {
        const val ENCABEZADO = "X-Request-Id"
    }
}

/**
 * Etiqueta de petición para comandos reintentables. Su valor es el `clientEventId` del comando,
 * el mismo en cada reintento (nunca se genera uno nuevo al retransmitir).
 * Con Retrofit se adjunta con `@Tag idempotencyKey: IdempotencyKey`.
 */
data class IdempotencyKey(val valor: String)

class IdempotencyKeyInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()
        val clave = peticion.tag(IdempotencyKey::class.java)
            ?: return chain.proceed(peticion)
        return chain.proceed(peticion.newBuilder().header(ENCABEZADO, clave.valor).build())
    }

    companion object {
        const val ENCABEZADO = "Idempotency-Key"
    }
}
