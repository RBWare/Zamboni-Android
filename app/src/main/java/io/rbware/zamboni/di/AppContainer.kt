package io.rbware.zamboni.di

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import io.rbware.zamboni.BuildConfig
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.news.NewsRepository
import io.rbware.zamboni.data.news.RssNewsRepository
import io.rbware.zamboni.data.news.StaticNewsSourceRepository
import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.preferences.AhlTeamIdMigration
import io.rbware.zamboni.data.preferences.EnabledLeaguesMigration
import io.rbware.zamboni.data.preferences.LegacyPreferencesMigration
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.remote.GamesheetApi
import io.rbware.zamboni.data.remote.HockeyNerdApi
import io.rbware.zamboni.data.remote.HockeyTechApi
import io.rbware.zamboni.data.remote.NhlApi
import io.rbware.zamboni.data.scores.FallbackScoresRepository
import io.rbware.zamboni.data.scores.ApiKeyProvider
import io.rbware.zamboni.data.scores.GamesheetNahlRepository
import io.rbware.zamboni.data.scores.HockeyNerdScoresRepository
import io.rbware.zamboni.data.scores.HockeyTechLeague
import io.rbware.zamboni.data.scores.HockeyTechRepository
import io.rbware.zamboni.data.scores.MultiLeagueScoresRepository
import io.rbware.zamboni.data.scores.apiGameIdOrNull
import io.rbware.zamboni.data.scores.isNahlGameId
import io.rbware.zamboni.data.scores.NhlScoresRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import io.rbware.zamboni.data.social.MastodonClient
import io.rbware.zamboni.data.social.SocialFeedRepository
import io.rbware.zamboni.data.social.SocialSourceRepository
import io.rbware.zamboni.data.social.YouTubeClient
import io.rbware.zamboni.data.teams.CombinedTeamRepository
import io.rbware.zamboni.data.teams.PlaceholderTeamRepository
import io.rbware.zamboni.data.teams.TeamRepository
import io.rbware.zamboni.notifications.AndroidLiveUpdateHost
import io.rbware.zamboni.notifications.NotificationChecker
import io.rbware.zamboni.notifications.NotificationStateStore
import io.rbware.zamboni.notifications.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

private val Context.userPreferencesDataStore by preferencesDataStore(
    name = "user_preferences",
    produceMigrations = { listOf(LegacyPreferencesMigration, AhlTeamIdMigration, EnabledLeaguesMigration) },
)

private val Context.notificationStateDataStore by preferencesDataStore(name = "notification_state")

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
            val request = chain.request()
            // Requests that need a specific User-Agent (e.g. YouTube pages) set their own.
            if (request.header("User-Agent") != null) return@addInterceptor chain.proceed(request)
            chain.proceed(
                request.newBuilder()
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
    val gamesheetKeyProvider = ApiKeyProvider(
        userPreferencesRepository.userPreferences.map { it.gamesheetApiKey },
        BuildConfig.GAMESHEET_API_KEY,
    )

    val nahlRepository = GamesheetNahlRepository(
        api = Retrofit.Builder()
            .baseUrl(GamesheetApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GamesheetApi.json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(),
        apiKey = gamesheetKeyProvider::current,
    )

    /** One Retrofit client for every HockeyTech league; each call names its league. */
    private val hockeyTechApi: HockeyTechApi = Retrofit.Builder()
        .baseUrl(HockeyTechApi.BASE_URL)
        .client(okHttpClient)
        .build()
        .create()

    /**
     * The HockeyTech keys for AHL and SPHL data: the one entered in Settings, else the public
     * key the league's own website uses.
     */
    val ahlKeyProvider = ApiKeyProvider(
        userPreferencesRepository.userPreferences.map { it.ahlApiKey },
        HockeyTechLeague.AHL.publicKey,
    )

    val sphlKeyProvider = ApiKeyProvider(
        userPreferencesRepository.userPreferences.map { it.sphlApiKey },
        HockeyTechLeague.SPHL.publicKey,
    )

    val ahlRepository = HockeyTechRepository(HockeyTechLeague.AHL, hockeyTechApi, ahlKeyProvider::current)

    val sphlRepository = HockeyTechRepository(HockeyTechLeague.SPHL, hockeyTechApi, sphlKeyProvider::current)

    /** Static NHL teams. The NHL scores sources use it to fill in full team names. */
    private val placeholderTeamRepository = PlaceholderTeamRepository()

    // TODO: replace the NHL placeholder list with an API once specified.
    val teamRepository: TeamRepository =
        CombinedTeamRepository(listOf(placeholderTeamRepository, ahlRepository, sphlRepository, nahlRepository))

    /**
     * NHL from the NHL API (hockeynerd.com as a fallback if it fails), the AHL and SPHL from
     * HockeyTech and the NAHL from Gamesheet. If one league fails to load, the others are still shown.
     */
    val scoresRepository: ScoresRepository = MultiLeagueScoresRepository(
        sources = listOfNotNull(
            MultiLeagueScoresRepository.Source(
                leagueId = Leagues.NHL.id,
                repository = FallbackScoresRepository(
                    primary = NhlScoresRepository(nhlApi, placeholderTeamRepository),
                    fallback = HockeyNerdScoresRepository(hockeyNerdApi, placeholderTeamRepository),
                ),
                ownsGameId = { apiGameIdOrNull(it) != null },
            ),
            MultiLeagueScoresRepository.Source(Leagues.AHL.id, ahlRepository, HockeyTechLeague.AHL::ownsGameId),
            MultiLeagueScoresRepository.Source(Leagues.SPHL.id, sphlRepository, HockeyTechLeague.SPHL::ownsGameId),
            // Shows nothing (and no NAHL filter chip) while there is no Gamesheet key.
            MultiLeagueScoresRepository.Source(Leagues.NAHL.id, nahlRepository, ::isNahlGameId),
        ),
        enabledLeagueIds = { userPreferencesRepository.userPreferences.first().enabledLeagueIds },
        onLeagueFailed = { Log.w("Scores", "A league failed to load: $it") },
    )

    /** Available feeds; add new ones in [io.rbware.zamboni.data.news.NewsSources]. */
    val newsSourceRepository: NewsSourceRepository = StaticNewsSourceRepository()

    val newsRepository: NewsRepository = RssNewsRepository(okHttpClient)

    /** Built-in accounts (see [io.rbware.zamboni.data.social.SocialSources]) plus the user's own. */
    val socialSourceRepository = SocialSourceRepository(userPreferencesRepository)

    val mastodonClient = MastodonClient(okHttpClient)

    val youTubeClient = YouTubeClient(okHttpClient)

    val socialFeedRepository = SocialFeedRepository(mastodonClient, youTubeClient)

    val notifier = Notifier(context)

    /** Used by the background notification checks; see [io.rbware.zamboni.notifications.NotificationScheduler]. */
    val notificationChecker = NotificationChecker(
        userPreferencesRepository = userPreferencesRepository,
        scoresRepository = scoresRepository,
        teamRepository = teamRepository,
        newsSourceRepository = newsSourceRepository,
        newsRepository = newsRepository,
        stateStore = NotificationStateStore(context.notificationStateDataStore),
        notifier = notifier,
        liveUpdates = AndroidLiveUpdateHost(context),
    )
}
