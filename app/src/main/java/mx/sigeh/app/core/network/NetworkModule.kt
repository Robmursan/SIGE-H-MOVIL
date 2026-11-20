package mx.sigeh.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import mx.sigeh.app.BuildConfig
import mx.sigeh.app.core.network.iam.IamApi
import mx.sigeh.app.core.network.iam.OperacionesApi
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Interceptores que solo existen en debug (registro HTTP). En release el conjunto está vacío. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugInterceptors

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkMultibindsModule {
    @Multibinds
    @DebugInterceptors
    abstract fun debugInterceptors(): Set<Interceptor>
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig =
        NetworkConfig(urlBase = BuildConfig.API_BASE_URL, iamRealSolicitado = BuildConfig.IAM_REAL)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        sesionAuthenticator: SesionAuthenticator,
        requestIdInterceptor: RequestIdInterceptor,
        idempotencyKeyInterceptor: IdempotencyKeyInterceptor,
        @DebugInterceptors debugInterceptors: Set<@JvmSuppressWildcards Interceptor>,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(requestIdInterceptor)
        .addInterceptor(idempotencyKeyInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(sesionAuthenticator)
        .apply { debugInterceptors.forEach { addInterceptor(it) } }
        .build()

    @Provides
    @Singleton
    fun provideRetrofitFactory(client: OkHttpClient, json: Json, config: NetworkConfig): RetrofitFactory =
        RetrofitFactory(client, json, config)

    /** Solo se construye en modo IAM real (los repositorios Fake nunca lo piden). */
    @Provides
    @Singleton
    fun provideRetrofit(factory: RetrofitFactory): Retrofit = factory.crear()

    @Provides
    @Singleton
    fun provideIamApi(retrofit: Retrofit): IamApi = retrofit.create(IamApi::class.java)

    @Provides
    @Singleton
    fun provideOperacionesApi(retrofit: Retrofit): OperacionesApi = retrofit.create(OperacionesApi::class.java)
}

/**
 * Crea Retrofit solo cuando hay URL base. Las interfaces existen únicamente para rutas publicadas
 * por SIGE-H-WEB (IAM V2 y meta/version); el resto sigue pendiente (docs/mobile/contrato-pendiente.md).
 */
class RetrofitFactory(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: NetworkConfig,
) {
    fun crear(): Retrofit {
        check(config.configurada) {
            "Sin URL base: define sigeh.api.baseUrl en local.properties o como propiedad de Gradle."
        }
        return Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
