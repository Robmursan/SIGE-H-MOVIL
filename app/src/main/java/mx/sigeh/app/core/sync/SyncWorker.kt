package mx.sigeh.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Envía la cola local. Restricción de red conectada, backoff exponencial y trabajo único (KEEP)
 * vienen de [WorkManagerSyncScheduler]. En M0 el remoto es `FakeSyncRepository`.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: SyncProcessor,
    private val retryPolicy: RetryPolicy,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = resultadoPara(processor.procesar(), runAttemptCount, retryPolicy)

    companion object {
        const val NOMBRE_UNICO = "sigeh-sync"

        /** Traduce el resultado del envío a WorkManager. Sesión vencida = fallo: detiene la cola. */
        fun resultadoPara(outcome: SyncOutcome, runAttemptCount: Int, policy: RetryPolicy): Result = when (outcome) {
            SyncOutcome.COMPLETADO -> Result.success()
            SyncOutcome.REINTENTAR ->
                if (policy.puedeReintentar(runAttemptCount + 1)) Result.retry() else Result.failure()
            SyncOutcome.SESION_VENCIDA -> Result.failure()
        }
    }
}
