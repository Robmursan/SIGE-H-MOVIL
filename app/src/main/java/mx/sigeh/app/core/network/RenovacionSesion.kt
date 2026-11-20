package mx.sigeh.app.core.network

import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import mx.sigeh.app.core.auth.SessionStore
import mx.sigeh.app.core.common.Reloj
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.core.network.iam.RefreshRequest
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Renueva el access token de IAM V2 (vida de 300 s) con el refresh de un solo uso.
 *
 * Reglas del backend que se respetan aquí:
 * - El refresh rota en cada uso y un **replay revoca toda la familia**: las renovaciones se
 *   serializan y, si otro hilo ya renovó, se reutiliza su resultado sin volver a llamar.
 * - 400/401 en el refresh = sesión no recuperable: se marca vencida (la app pide login).
 * - 429/503 o red caída = transitorio: no se invalida la sesión.
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val sessionStore: SessionStore,
    private val iamApi: Provider<IamApi>,
    private val reloj: Reloj,
) {
    private val candado = Any()

    /** Token para una petición protegida; lo renueva antes si vence en menos de [MARGEN_MS]. */
    fun tokenVigente(): String? {
        val sesion = sessionStore.sesionEnMemoria()?.takeUnless { it.vencida } ?: return null
        val expira = sesion.accessExpiraEnMillis
        val porVencer = expira != null && expira - reloj.ahoraMillis() <= MARGEN_MS
        return if (porVencer && sesion.refreshToken != null) renovar(sesion.accessToken) else sesion.accessToken
    }

    /**
     * Renueva una sola vez por token rechazado. Devuelve el nuevo access token o null si no se pudo.
     * @param tokenRechazado access token con el que falló la petición (o el que está por vencer).
     */
    fun renovar(tokenRechazado: String?): String? = synchronized(candado) {
        val sesion = sessionStore.sesionEnMemoria()?.takeUnless { it.vencida } ?: return null
        // Otro hilo ya renovó mientras se esperaba el candado: no se reenvía el refresh (sería replay).
        if (tokenRechazado != null && sesion.accessToken != tokenRechazado) return sesion.accessToken
        val refresh = sesion.refreshToken ?: return null

        val respuesta = try {
            iamApi.get().refrescar(RefreshRequest(refresh)).execute()
        } catch (_: IOException) {
            return null
        }
        val tokens = respuesta.body()
        when {
            respuesta.isSuccessful && tokens != null -> {
                val renovada = sesion.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    sessionId = tokens.sessionId,
                    accessExpiraEnMillis = reloj.ahoraMillis() + tokens.expiresIn * 1_000,
                )
                runBlocking { sessionStore.guardar(renovada) }
                renovada.accessToken
            }
            respuesta.code() == 400 || respuesta.code() == 401 -> {
                runBlocking { sessionStore.marcarVencida() }
                null
            }
            else -> null
        }
    }

    companion object {
        /** Se renueva 30 s antes del vencimiento para no enviar tokens a punto de expirar. */
        const val MARGEN_MS = 30_000L
    }
}

/** Ante un 401 de una ruta protegida, renueva una vez y reintenta; nunca entra en bucle. */
class SesionAuthenticator @Inject constructor(
    private val tokenRefresher: TokenRefresher,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val peticion = response.request
        if (peticion.esPublica() || response.priorResponse != null) return null
        val usado = peticion.header(ENCABEZADO_AUTORIZACION)?.removePrefix(PREFIJO_BEARER)
        val nuevo = tokenRefresher.renovar(usado) ?: return null
        return peticion.newBuilder().header(ENCABEZADO_AUTORIZACION, PREFIJO_BEARER + nuevo).build()
    }
}

internal const val ENCABEZADO_AUTORIZACION = "Authorization"
internal const val PREFIJO_BEARER = "Bearer "

/** Rutas sin sesión: no llevan bearer ni disparan renovaciones (evita recursión en /auth/refresh). */
internal fun Request.esPublica(): Boolean {
    val ruta = url.encodedPath
    return ruta.endsWith("/auth/login") ||
        ruta.endsWith("/auth/refresh") ||
        ruta.endsWith("/meta/version") ||
        ruta.contains("/health")
}
