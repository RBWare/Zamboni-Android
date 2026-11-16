package io.rbware.zamboni

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.rbware.zamboni.data.logos.TeamLogoPrefetchWorker
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.di.AppContainer
import io.rbware.zamboni.notifications.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch

class ZamboniApplication : Application(), SingletonImageLoader.Factory {
    /** Lives as long as the process; for app-wide work that isn't tied to a screen. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Download any team logos that aren't cached yet so they're ready wherever teams appear.
        TeamLogoPrefetchWorker.enqueue(this)

        container.notifier.createChannels()
        // Start, stop or refresh background notification checks whenever the settings they
        // depend on change. The first value only makes sure checks are scheduled: this also
        // runs when WorkManager starts the process for a check, which must not be replaced.
        container.userPreferencesRepository.userPreferences
            .map { prefs ->
                NotificationInputs(
                    enabled = prefs.onboardingComplete && prefs.notificationTypes.isNotEmpty(),
                    types = prefs.notificationTypes,
                    favoriteTeamIds = prefs.favoriteTeamIds,
                    newsSourceIds = prefs.newsSourceIds,
                )
            }
            .distinctUntilChanged()
            .withIndex()
            .onEach { (index, inputs) -> NotificationScheduler.sync(this, inputs.enabled, checkNow = index > 0) }
            .launchIn(applicationScope)
    }

    /**
     * The app is on screen: run a notification check now, while Android allows it to start the
     * Live Updates service, so a favorite team's game in progress gets one right away.
     */
    fun onAppVisible() {
        applicationScope.launch {
            val prefs = container.userPreferencesRepository.userPreferences.first()
            if (!prefs.onboardingComplete || NotificationType.LIVE_UPDATES !in prefs.notificationTypes) return@launch
            val next = try {
                container.notificationChecker.check()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ZamboniApplication", "Check on open failed: $e")
                null
            }
            next?.let { NotificationScheduler.scheduleNext(this@ZamboniApplication, it, fromChain = false) }
        }
    }

    private data class NotificationInputs(
        val enabled: Boolean,
        val types: Set<NotificationType>,
        val favoriteTeamIds: Set<String>,
        val newsSourceIds: Set<String>,
    )

    /**
     * App-wide image loader, used for team logos.
     *
     * Logos are downloaded once (see TeamLogoPrefetcher) and then served from the on-disk cache.
     * Coil's default cache strategy ignores HTTP cache headers, so a cached logo is reused
     * until it is evicted (LRU, past [IMAGE_DISK_CACHE_BYTES]) or the system clears app cache.
     * Recently shown logos are also kept decoded in memory for fast scrolling.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.okHttpClient }))
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.15).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val IMAGE_DISK_CACHE_BYTES = 64L * 1024 * 1024
    }
}
