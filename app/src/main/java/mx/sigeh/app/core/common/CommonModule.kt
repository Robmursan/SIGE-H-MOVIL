package mx.sigeh.app.core.common

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import mx.sigeh.app.BuildConfig
import mx.sigeh.app.core.model.SigehJson

/** Alcance de corrutinas que vive lo mismo que la aplicación. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonBindsModule {
    @Binds
    abstract fun bindReloj(impl: RelojSistema): Reloj

    @Binds
    abstract fun bindGeneradorIds(impl: GeneradorUuid): GeneradorIds
}

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideJson(): Json = SigehJson

    @Provides
    @Singleton
    fun provideInterruptores(): Interruptores = Interruptores(funcionesFuturas = BuildConfig.FUNCIONES_FUTURAS)
}
