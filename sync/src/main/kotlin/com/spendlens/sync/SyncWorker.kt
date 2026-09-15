package com.spendlens.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.spendlens.core.data.sync.Synchronizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runs one sync pass in the background.
 *
 * Only a network failure is retried. Anything else — a server that breaks the protocol, a response
 * that will not parse — would fail identically on every attempt, so it is left to fail the work, where
 * WorkManager logs it. Local changes are never lost either way: they stay `PENDING` for the next sync.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val synchronizer: Synchronizer,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            try {
                val report = synchronizer.sync()
                Log.i(TAG, "Sync finished: $report")
                Result.success()
            } catch (e: IOException) {
                // runAttemptCount counts earlier attempts, so this one is number runAttemptCount + 1.
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    Log.w(TAG, "Sync failed $MAX_ATTEMPTS times; waiting for the next trigger", e)
                    Result.failure()
                } else {
                    Log.i(TAG, "Sync failed on the network; retrying with backoff", e)
                    Result.retry()
                }
            }

        companion object {
            private const val TAG = "SyncWorker"

            /**
             * With exponential backoff from 30 s that is about 7.5 minutes of retrying. After that a
             * broken server stops costing battery; the periodic sync and the next save try again.
             */
            internal const val MAX_ATTEMPTS = 5

            private const val BACKOFF_SECONDS = 30L
            private const val PERIODIC_HOURS = 4L

            /** Waiting for a connection is WorkManager's job, not a failed attempt's. */
            private val constraints = Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            internal fun oneTimeRequest(): OneTimeWorkRequest =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()

            /**
             * The safety net: picks up other devices' changes while this one is idle, and anything a
             * missed trigger left pending. Every few hours is plenty for expenses.
             */
            internal fun periodicRequest(): PeriodicWorkRequest =
                PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()
        }
    }
