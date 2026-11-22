package dev.cpsaul.noticias.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.cpsaul.noticias.data.factcheck.FactCheckRepository
import dev.cpsaul.noticias.data.local.AppPreferences
import dev.cpsaul.noticias.data.local.ImageDiskCache
import dev.cpsaul.noticias.data.local.NewsDatabase
import dev.cpsaul.noticias.data.remote.FeedNetworkFetcher
import dev.cpsaul.noticias.diagnostics.ErrorLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * §6 Estrategia de Sincronización Offline mediante WorkManager:
 * Restricciones estrictas de conectividad (NetworkType.CONNECTED) y batería no baja (BatteryNotLow).
 * Sincroniza feeds RSS/Atom/JSON, extrae Markdown offline, cachea imágenes y actualiza ClaimReview.
 */
class PeriodicSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = NewsDatabase.getInstance(applicationContext)
            val dao = db.newsDao()
            val prefs = AppPreferences(applicationContext)
            val imageCache = ImageDiskCache(File(applicationContext.filesDir, "article_images"))
            val fetcher = FeedNetworkFetcher(imageDiskCache = imageCache)
            val factCheckRepo = FactCheckRepository(dao)

            factCheckRepo.ensureSeedCatalogLoaded(applicationContext)

            val feeds = dao.getAllFeeds()
            val existingIds = dao.getExistingArticleIds().toMutableSet()

            for (feed in feeds) {
                val res = fetcher.fetchFeed(feed, existingIds)
                if (res.error == null) {
                    dao.updateFeedLastSynced(feed.id, res.feed.lastSyncedAt)
                    if (res.newArticles.isNotEmpty()) {
                        dao.upsertArticles(res.newArticles)
                        existingIds.addAll(res.newArticles.map { it.id })
                    }
                }
            }

            val dumpUrl = prefs.state.value.factCheckConfig.claimReviewDumpUrl
            if (dumpUrl.isNotBlank()) {
                runCatching { factCheckRepo.syncRemoteDump(dumpUrl) }
            }

            Result.success()
        } catch (e: Exception) {
            ErrorLog.record(applicationContext, "Sincronización periódica", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "noticias_periodic_sync"

        fun defaultConstraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(4, TimeUnit.HOURS)
                .setConstraints(defaultConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
