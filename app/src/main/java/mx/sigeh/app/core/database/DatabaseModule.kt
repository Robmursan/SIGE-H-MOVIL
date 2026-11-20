package mx.sigeh.app.core.database

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import mx.sigeh.app.core.database.dao.CachedProgramacionDao
import mx.sigeh.app.core.database.dao.PendingCommandDao
import mx.sigeh.app.core.database.dao.SyncReceiptDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SigehDatabase =
        // Sin fallbackToDestructiveMigration: perder la cola local borraría capturas sin enviar.
        Room.databaseBuilder(context, SigehDatabase::class.java, SigehDatabase.NOMBRE).build()

    @Provides
    fun providePendingCommandDao(database: SigehDatabase): PendingCommandDao = database.pendingCommandDao()

    @Provides
    fun provideSyncReceiptDao(database: SigehDatabase): SyncReceiptDao = database.syncReceiptDao()

    @Provides
    fun provideCachedProgramacionDao(database: SigehDatabase): CachedProgramacionDao =
        database.cachedProgramacionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PurgaModule {
    @Binds
    abstract fun bindPurgaLocal(impl: RoomPurgaLocal): PurgaLocal
}
