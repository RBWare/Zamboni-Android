package io.rbware.zamboni.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.rbware.zamboni.BuildConfig
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.news.PlaceholderNewsSourceRepository
import io.rbware.zamboni.data.preferences.LegacyTeamIdMigration
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.remote.HockeyNerdApi
import io.rbware.zamboni.data.scores.HockeyNerdScoresRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import io.rbware.zamboni.data.teams.PlaceholderTeamRepository
import io.rbware.zamboni.data.teams.TeamRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = "user_preferences",
    produceMigrations = { listOf(LegacyTeamIdMigration) },
)

/**
 * Manual dependency container, created once in [io.rbware.zamboni.ZamboniApplication].
 * Swap the placeholder repositories for real implementations here as they are built.
 */
class AppContainer(context: Context) {
    val userPreferencesRepository = UserPreferencesRepository(context.userPreferencesDataStore)

    /** Shared by the API client and the image loader so they reuse connections. */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Zamboni-Android/${BuildConfig.VERSION_NAME}")
                    .build(),
            )
        }
        .build()

    private val hockeyNerdApi: HockeyNerdApi = Retrofit.Builder()
        .baseUrl(HockeyNerdApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(HockeyNerdApi.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    // TODO: replace with a hockeynerd.com teams endpoint once specified.
    val teamRepository: TeamRepository = PlaceholderTeamRepository()

    val scoresRepository: ScoresRepository = HockeyNerdScoresRepository(hockeyNerdApi, teamRepository)

    // TODO: replace with the real list of RSS feeds once specified.
    val newsSourceRepository: NewsSourceRepository = PlaceholderNewsSourceRepository()
}
