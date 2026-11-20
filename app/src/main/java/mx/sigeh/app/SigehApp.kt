package mx.sigeh.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Aplicación SIGE-H. WorkManager se configura con [HiltWorkerFactory] para inyectar dependencias
 * en `SyncWorker` (el inicializador automático está desactivado en el manifiesto).
 */
@HiltAndroidApp
class SigehApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.INFO else Log.ERROR)
            .build()
}
