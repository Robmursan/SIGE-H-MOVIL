package mx.sigeh.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Registro HTTP solo para builds de debug (la dependencia es `debugImplementation`).
 * - En las rutas de autenticación (`/auth/...`) solo se registran encabezados: los cuerpos llevan
 *   contraseñas y tokens.
 * - En el resto se registran cuerpos, siempre ocultando los encabezados con credenciales y la
 *   `Idempotency-Key` (no debe quedar en Logcat junto a nombres del pase de lista).
 * Aun en debug, usar solo cuentas y datos ficticios.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    @Provides
    @IntoSet
    @DebugInterceptors
    fun provideLoggingInterceptor(): Interceptor {
        val soloEncabezados = registro(HttpLoggingInterceptor.Level.HEADERS)
        val conCuerpos = registro(HttpLoggingInterceptor.Level.BODY)
        return Interceptor { chain ->
            if (chain.request().url.encodedPath.contains("/auth/")) {
                soloEncabezados.intercept(chain)
            } else {
                conCuerpos.intercept(chain)
            }
        }
    }

    private fun registro(nivel: HttpLoggingInterceptor.Level) = HttpLoggingInterceptor().apply {
        level = nivel
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        redactHeader(IdempotencyKeyInterceptor.ENCABEZADO)
    }
}
