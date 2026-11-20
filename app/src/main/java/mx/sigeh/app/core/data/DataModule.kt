package mx.sigeh.app.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import mx.sigeh.app.core.data.fake.FakeAlcanceRepository
import mx.sigeh.app.core.data.fake.FakeAuthRepository
import mx.sigeh.app.core.data.fake.FakeCoberturaRepository
import mx.sigeh.app.core.data.fake.FakeIncidenciaRepository
import mx.sigeh.app.core.data.fake.FakeMovimientosRepository
import mx.sigeh.app.core.data.fake.FakePaseListaRepository
import mx.sigeh.app.core.data.fake.FakeProgramacionRepository
import mx.sigeh.app.core.data.fake.FakeServidorRepository
import mx.sigeh.app.core.data.fake.FakeSyncRepository
import mx.sigeh.app.core.data.remote.IamAuthRepository
import mx.sigeh.app.core.data.remote.RemoteServidorRepository
import mx.sigeh.app.core.network.NetworkConfig

/**
 * ÚNICO punto para elegir entre implementaciones Fake y reales.
 *
 * Estado M1 (backend SIGE-H-WEB, main 2604aa6):
 * - Real (si `sigeh.api.iam=real` y hay URL base): Auth (IAM V2) y Servidor (`/meta/version`).
 * - Fake: el resto, porque el backend todavía no publica esas rutas.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAlcanceRepository(impl: FakeAlcanceRepository): AlcanceRepository

    @Binds
    abstract fun bindProgramacionRepository(impl: FakeProgramacionRepository): ProgramacionRepository

    @Binds
    abstract fun bindPaseListaRepository(impl: FakePaseListaRepository): PaseListaRepository

    @Binds
    abstract fun bindIncidenciaRepository(impl: FakeIncidenciaRepository): IncidenciaRepository

    @Binds
    abstract fun bindMovimientosRepository(impl: FakeMovimientosRepository): MovimientosRepository

    @Binds
    abstract fun bindCoberturaRepository(impl: FakeCoberturaRepository): CoberturaRepository

    @Binds
    abstract fun bindSyncRepository(impl: FakeSyncRepository): SyncRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SeleccionApiModule {

    /** Provider: la implementación no elegida nunca se construye (en Fake no se crea Retrofit). */
    @Provides
    @Singleton
    fun provideAuthRepository(
        config: NetworkConfig,
        real: Provider<IamAuthRepository>,
        fake: Provider<FakeAuthRepository>,
    ): AuthRepository = if (config.iamReal) real.get() else fake.get()

    @Provides
    @Singleton
    fun provideServidorRepository(
        config: NetworkConfig,
        real: Provider<RemoteServidorRepository>,
        fake: Provider<FakeServidorRepository>,
    ): ServidorRepository = if (config.iamReal) real.get() else fake.get()
}
