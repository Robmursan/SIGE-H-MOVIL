package mx.sigeh.app.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Política de reintentos de la cola. El backoff es exponencial y lo aplica WorkManager. */
data class RetryPolicy(
    val maxIntentos: Int = 6,
    val backoffInicialSegundos: Long = 30,
) {
    fun puedeReintentar(intentosRealizados: Int): Boolean = intentosRealizados < maxIntentos
}

interface SyncScheduler {
    fun programar()

    fun cancelar()
}

/**
 * Trabajo único con política KEEP: si ya hay un envío en cola o en backoff, no se duplica.
 * Solo corre con red conectada.
 */
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val retryPolicy: RetryPolicy,
) : SyncScheduler {

    override fun programar() {
        val peticion = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, retryPolicy.backoffInicialSegundos, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SyncWorker.NOMBRE_UNICO, ExistingWorkPolicy.KEEP, peticion)
    }

    override fun cancelar() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.NOMBRE_UNICO)
    }
}
