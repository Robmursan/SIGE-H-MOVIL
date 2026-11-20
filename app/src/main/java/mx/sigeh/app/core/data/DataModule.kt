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
import mx.sigeh.app.core.data.fake.FakeEtiquetasContexto
import mx.sigeh.app.core.data.fake.FakeIncidenciaRepository
import mx.sigeh.app.core.data.fake.FakeMovimientosRepository
import mx.sigeh.app.core.data.fake.FakePaseListaOfflineRepository
import mx.sigeh.app.core.data.fake.FakePaseListaRepository
import mx.sigeh.app.core.data.fake.FakeProgramacionRepository
import mx.sigeh.app.core.data.fake.FakeServidorRepository
import mx.sigeh.app.core.data.fake.FakeSesionesRepository
import mx.sigeh.app.core.data.fake.FakeSyncRepository
import mx.sigeh.app.core.data.remote.IamAuthRepository
import mx.sigeh.app.core.data.remote.RemoteServidorRepository
import mx.sigeh.app.core.data.remote.RemotoPaseListaRepository
import mx.sigeh.app.core.data.remote.RemotoSesionesRepository
import mx.sigeh.app.core.network.NetworkConfig

/**
 * ÚNICO punto para elegir entre implementaciones Fake y reales.
 *
 * Estado M2 (contrato `sigeh-v1`, SIGE-H-WEB main 2d1ec62):
 * - Con `sigeh.api.iam=real` y URL base: Auth (IAM V2), Servidor (`/meta/version`), Sesiones
 *   (`/auth/sessions`) y EtiquetasContexto (texto con el final del UUID).
 * - Con además `sigeh.api.asistencia=real`: pase de lista y captura (`/pases-lista` y
 *   `PUT /programaciones/{id}/asistencia`).
 * - Siempre Fake: lo que el backend todavía no publica (alcances del catálogo M0, agenda,
 *   incidencias, movimientos, cobertura, sincronización y el pase offline de M0).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAlcanceRepository(impl: FakeAlcanceRepository): AlcanceRepository

    @Binds
    abstract fun bindProgramacionRepository(impl: FakeProgramacionRepository): ProgramacionRepository

    @Binds
    abstract fun bindPaseListaOfflineRepository(impl: FakePaseListaOfflineRepository): PaseListaOfflineRepository

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

    @Provides
    @Singleton
    fun providePaseListaRepository(
        config: NetworkConfig,
        real: Provider<RemotoPaseListaRepository>,
        fake: Provider<FakePaseListaRepository>,
    ): PaseListaRepository = if (config.asistenciaReal) real.get() else fake.get()

    @Provides
    @Singleton
    fun provideSesionesRepository(
        config: NetworkConfig,
        real: Provider<RemotoSesionesRepository>,
        fake: Provider<FakeSesionesRepository>,
    ): SesionesRepository = if (config.iamReal) real.get() else fake.get()

    /** Los identificadores vienen de `/auth/me`: con IAM real son UUID sin nombre. */
    @Provides
    @Singleton
    fun provideEtiquetasContexto(
        config: NetworkConfig,
        real: Provider<EtiquetasPorIdentificador>,
        fake: Provider<FakeEtiquetasContexto>,
    ): EtiquetasContexto = if (config.iamReal) real.get() else fake.get()
}
