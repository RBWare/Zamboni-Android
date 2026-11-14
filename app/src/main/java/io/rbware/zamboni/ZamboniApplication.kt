package io.rbware.zamboni

import android.app.Application
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
import io.rbware.zamboni.di.AppContainer

class ZamboniApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Download any team logos that aren't cached yet so they're ready wherever teams appear.
        TeamLogoPrefetchWorker.enqueue(this)
    }

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
                add(OkHttpNetworkFetcherFactory())
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
