package io.rbware.zamboni.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import io.rbware.zamboni.BuildConfig
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.news.NewsRepository
import io.rbware.zamboni.data.news.RssNewsRepository
import io.rbware.zamboni.data.news.StaticNewsSourceRepository
import io.rbware.zamboni.data.preferences.LegacyPreferencesMigration
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.remote.GamesheetApi
import io.rbware.zamboni.data.remote.HockeyNerdApi
import io.rbware.zamboni.data.remote.NhlApi
import io.rbware.zamboni.data.scores.FallbackScoresRepository
import io.rbware.zamboni.data.scores.GamesheetKeyProvider
import io.rbware.zamboni.data.scores.GamesheetNahlRepository
import io.rbware.zamboni.data.scores.HockeyNerdScoresRepository
import io.rbware.zamboni.data.scores.MultiLeagueScoresRepository
import io.rbware.zamboni.data.scores.apiGameIdOrNull
import io.rbware.zamboni.data.scores.isNahlGameId
import io.rbware.zamboni.data.scores.NhlScoresRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import io.rbware.zamboni.data.teams.CombinedTeamRepository
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
    produceMigrations = { listOf(LegacyPreferencesMigration) },
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

    private val nhlApi: NhlApi = Retrofit.Builder()
        .baseUrl(NhlApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(NhlApi.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    /**
     * The Gamesheet key for NAHL data: the one entered in Settings, else the one built into
     * debug builds from local.properties. Release builds have none built in.
     */
    val gamesheetKeyProvider = GamesheetKeyProvider(userPreferencesRepository, BuildConfig.GAMESHEET_API_KEY)

    val nahlRepository = GamesheetNahlRepository(
        api = Retrofit.Builder()
            .baseUrl(GamesheetApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GamesheetApi.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(),
        apiKey = gamesheetKeyProvider::current,
    )

    /** Static NHL teams (and AHL, when enabled in PlaceholderHockeyData). The NHL scores sources use it to fill in full team names. */
    private val placeholderTeamRepository = PlaceholderTeamRepository()

    // TODO: replace the NHL and AHL placeholder list with an API once specified.
    val teamRepository: TeamRepository = CombinedTeamRepository(listOf(placeholderTeamRepository, nahlRepository))

    /**
     * NHL from the NHL API (hockeynerd.com as a fallback if it fails), plus the NAHL from
     * Gamesheet. If one league fails to load, the other is still shown.
     */
    val scoresRepository: ScoresRepository = MultiLeagueScoresRepository(
        sources = listOfNotNull(
            MultiLeagueScoresRepository.Source(
                repository = FallbackScoresRepository(
                    primary = NhlScoresRepository(nhlApi, placeholderTeamRepository),
                    fallback = HockeyNerdScoresRepository(hockeyNerdApi, placeholderTeamRepository),
                ),
                ownsGameId = { apiGameIdOrNull(it) != null },
            ),
            // Shows nothing (and no NAHL filter chip) while there is no Gamesheet key.
            MultiLeagueScoresRepository.Source(nahlRepository, ::isNahlGameId),
        ),
        onLeagueFailed = { Log.w("Scores", "A league failed to load: $it") },
    )

    /** Available feeds; add new ones in [io.rbware.zamboni.data.news.NewsSources]. */
    val newsSourceRepository: NewsSourceRepository = StaticNewsSourceRepository()

    val newsRepository: NewsRepository = RssNewsRepository(okHttpClient)
}
