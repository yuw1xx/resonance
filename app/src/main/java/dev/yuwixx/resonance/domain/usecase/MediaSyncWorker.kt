package dev.yuwixx.resonance.domain.usecase

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.yuwixx.resonance.data.repository.MusicRepository
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager task that syncs the local Room database
 * with the system MediaStore. Runs on a periodic schedule and also
 * on demand via LibraryViewModel.syncLibrary().
 */
@HiltWorker
class MediaSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            musicRepository.syncWithMediaStore()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "media_sync"
        const val PERIODIC_WORK_NAME = "media_sync_periodic"

        /**
         * One-time sync request — triggered on library open or pull-to-refresh.
         */
        fun oneTimeRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

        /**
         * Periodic sync every 6 hours to pick up newly added/removed tracks.
         */
        fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<MediaSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
    }
}
