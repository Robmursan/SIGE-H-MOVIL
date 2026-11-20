package mx.sigeh.app.testutil

import java.util.concurrent.TimeUnit
import javax.inject.Provider
import mockwebserver3.MockWebServer
import mx.sigeh.app.core.auth.Session
import mx.sigeh.app.core.network.AuthInterceptor
import mx.sigeh.app.core.network.IdempotencyKeyInterceptor
import mx.sigeh.app.core.network.RequestIdInterceptor
import mx.sigeh.app.core.network.SesionAuthenticator
import mx.sigeh.app.core.network.TokenRefresher
import mx.sigeh.app.core.network.iam.IamApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Cadena real de OkHttp de la app (mismo orden que `NetworkModule.provideOkHttpClient`) contra un
 * servidor simulado, con una sesión de IAM V2 en memoria cuyo access token es `acceso-1`.
 */
class ClienteDePrueba(
    servidor: MockWebServer,
    reloj: RelojFijo,
    timeoutLecturaMs: Long? = null,
) {
    val sesiones = SessionStoreEnMemoria(
        Session(
            usuarioId = ContratoIam.USUARIO_ID,
            nombreCorto = "cuenta_ficticia",
            accessToken = "acceso-1",
            refreshToken = ContratoIam.REFRESH_1,
            sessionId = ContratoIam.SESION_ID,
            accessExpiraEnMillis = reloj.ahora + 200_000,
        ),
    )

    val retrofit: Retrofit

    init {
        // El refresher necesita IamApi, que a su vez sale del mismo Retrofit: se resuelve al usarse.
        var iamApi: IamApi? = null
        val refresher = TokenRefresher(sesiones, Provider { checkNotNull(iamApi) }, reloj)
        val cliente = OkHttpClient.Builder()
            .addInterceptor(RequestIdInterceptor())
            .addInterceptor(IdempotencyKeyInterceptor())
            .addInterceptor(AuthInterceptor(refresher))
            .authenticator(SesionAuthenticator(refresher))
            .apply { timeoutLecturaMs?.let { readTimeout(it, TimeUnit.MILLISECONDS) } }
            .build()
        retrofit = retrofitPara(servidor, cliente)
        iamApi = retrofit.create(IamApi::class.java)
    }

    inline fun <reified T> crear(): T = retrofit.create(T::class.java)
}
