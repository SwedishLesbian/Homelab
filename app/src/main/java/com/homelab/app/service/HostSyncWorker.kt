package com.homelab.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.homelab.app.data.repository.HostRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class HostSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val hostRepository: HostRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return hostRepository.refreshHosts()
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
    }

    companion object {
        private const val WORK_NAME = "host_sync"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<HostSyncWorker>(60, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelSync(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
