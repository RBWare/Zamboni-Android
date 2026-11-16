package io.rbware.zamboni.ui.scores

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.Broadcast
import io.rbware.zamboni.data.model.BroadcastMarket
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.RadioStream
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStat
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.scores.BroadcastNames
import io.rbware.zamboni.radio.LocalRadioPlayer
import io.rbware.zamboni.radio.RadioPlayer
import io.rbware.zamboni.radio.radioMediaId
import io.rbware.zamboni.ui.LocalShownGame
import io.rbware.zamboni.ui.components.TeamBadge
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId

private val MaxContentWidth = 640.dp
private val HeaderLogoSize = 64.dp

/**
 * Game detail pane: score header, scoring summary, team stats and game info.
 *
 * [listGame] (from the schedule) is shown immediately while full details load, and supplies
 * the team records, which the game endpoint doesn't include.
 */
/** How the details pane is dismissed: a back arrow when it fills the screen, an X when it sits beside the list. */
enum class DetailCloseStyle { BACK, CLOSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetail(
    gameId: String?,
    listGame: Game?,
    today: LocalDate,
    closeStyle: DetailCloseStyle,
    onClose: () -> Unit,
    /** Called with each fresh copy of the game, so the scores list can show the same. */
    onGameLoaded: (Game) -> Unit = {},
    viewModel: GameDetailViewModel = viewModel(factory = GameDetailViewModel.Factory),
) {
    LaunchedEffect(gameId) { viewModel.show(gameId) }
    // Lets the now-playing bar hide while this game's own radio controls are on screen.
    val shownGame = LocalShownGame.current
    DisposableEffect(gameId) {
        shownGame.gameId = gameId
        onDispose { if (shownGame.gameId == gameId) shownGame.gameId = null }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Ignore state that still belongs to the previously shown game.
    val current = state.takeIf { it.gameId == gameId }
    val details = current?.details
    val game = (details?.game ?: listGame)?.let { game ->
        game.copy(
            awayRecord = game.awayRecord ?: listGame?.awayRecord,
            homeRecord = game.homeRecord ?: listGame?.homeRecord,
            venue = game.venue ?: listGame?.venue,
        )
    }

    LaunchedEffect(details?.game) { details?.game?.let(onGameLoaded) }

    // Live updates while visible, on the API's suggested interval. Restarts after every load.
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoRefreshInterval = current?.autoRefreshInterval
    LaunchedEffect(autoRefreshInterval, gameId, details) {
        if (autoRefreshInterval == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(autoRefreshInterval)
                viewModel.refreshSilently()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    game?.let { Text(stringResource(R.string.game_matchup, it.away.abbreviation, it.home.abbreviation)) }
                },
                navigationIcon = {
                    if (closeStyle == DetailCloseStyle.BACK && gameId != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (closeStyle == DetailCloseStyle.CLOSE && gameId != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, stringResource(R.string.close))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (gameId == null || game == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.scores_select_game), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = current?.isRefreshing == true,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(16.dp)) {
                    if (current?.isLoading == true) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 16.dp))
                    }
                    ScoreHeader(game)
                    val mute by viewModel.muteState.collectAsStateWithLifecycle()
                    if (mute.canMute(game)) {
                        MuteGameRow(
                            muted = game.id in mute.mutedGameIds,
                            onMutedChange = { viewModel.setMuted(game.id, it) },
                        )
                    }
                    current?.error?.let { error ->
                        LoadError(error, onRetry = viewModel::refresh)
                    }
                    if (details != null) {
                        if (game.status !is GameStatus.Scheduled) {
                            ScoringSection(details, game)
                        }
                        if (details.teamStats.isNotEmpty()) {
                            TeamStatsSection(details.teamStats, game)
                        }
                        WhereToWatchSection(details, game)
                    }
                    GameInfoSection(game, today)
                }
            }
        }
    }
}

@Composable
private fun LoadError(error: GameDetailError, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(
                when (error) {
                    GameDetailError.NOT_FOUND -> R.string.game_not_found
                    GameDetailError.LOAD_FAILED -> R.string.game_load_failed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (error == GameDetailError.LOAD_FAILED) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

// region Header

@Composable
private fun ScoreHeader(game: Game) {
    // Top-aligned so both logos sit on the same line and both names start on the same line,
    // even when one name wraps to two lines.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        HeaderTeam(game.away, game.awayRecord, Modifier.weight(1f))
        // Centered on the logos rather than on the whole row. Not weighted, so the team names
        // get the remaining width.
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(min = 88.dp)
                .heightIn(min = HeaderLogoSize),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (game.awayScore != null && game.homeScore != null) {
                Text(
                    stringResource(R.string.game_score, game.awayScore, game.homeScore),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (game.status is GameStatus.Live) {
                LiveBadge(Modifier.padding(vertical = 4.dp))
            }
            Text(
                gameStatusText(game),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeaderTeam(game.home, game.homeRecord, Modifier.weight(1f))
    }
}

@Composable
private fun HeaderTeam(team: Team, record: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(team, size = HeaderLogoSize)
        Spacer(Modifier.height(8.dp))
        Text(team.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (record != null) {
            Text(
                record,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// endregion

// region Scoring

@Composable
private fun ScoringSection(details: GameDetails, game: Game) {
    SectionTitle(stringResource(R.string.game_section_scoring))
    if (details.goals.isEmpty()) {
        Text(
            stringResource(R.string.game_no_goals),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    details.goals.groupBy { it.period }.toSortedMap().forEach { (period, goals) ->
        Text(
            periodHeader(period, goals.first().isOvertime),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        goals.forEach { GoalRow(it, game) }
    }
}

@Composable
private fun periodHeader(period: Int, isOvertime: Boolean): String = when {
    period <= 3 && !isOvertime -> stringResource(R.string.game_period_header, periodLabel(period))
    period == 4 -> stringResource(R.string.game_period_overtime)
    else -> periodLabel(period)
}

@Composable
private fun GoalRow(goal: Goal, game: Game) {
    val team = if (goal.side == TeamSide.AWAY) game.away else game.home
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerHeadshot(goal.scorer, team)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    withTotal(goal.scorer),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                when (goal.strength) {
                    GoalStrength.POWER_PLAY -> GoalTag(stringResource(R.string.goal_tag_pp))
                    GoalStrength.SHORTHANDED -> GoalTag(stringResource(R.string.goal_tag_sh))
                    GoalStrength.EVEN -> Unit
                }
                if (goal.emptyNet) GoalTag(stringResource(R.string.goal_tag_en))
            }
            // Null when the source doesn't report assists; say nothing rather than "Unassisted".
            goal.assists?.let { assists ->
                Text(
                    if (assists.isEmpty()) {
                        stringResource(R.string.goal_unassisted)
                    } else {
                        assists.joinToString(", ") { withTotal(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(goal.timeInPeriod, style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.game_score, goal.awayScore, goal.homeScore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun withTotal(player: GoalPlayer): String =
    player.seasonTotal?.let { "${player.name} ($it)" } ?: player.name

/** Player headshot with the team logo in the corner; falls back to just the logo. */
@Composable
private fun PlayerHeadshot(player: GoalPlayer, team: Team) {
    Box(Modifier.size(48.dp)) {
        var failed by remember(player.headshotUrl) { mutableStateOf(false) }
        if (player.headshotUrl == null || failed) {
            TeamBadge(team, size = 48.dp)
        } else {
            AsyncImage(
                model = player.headshotUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
            ) {
                TeamBadge(team, size = 20.dp, modifier = Modifier.padding(1.dp))
            }
        }
    }
}

@Composable
private fun GoalTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(start = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

// endregion

// region Team stats

@Composable
private fun TeamStatsSection(stats: List<TeamStat>, game: Game) {
    SectionTitle(stringResource(R.string.game_section_team_stats))
    val background = MaterialTheme.colorScheme.background
    val awayFallback = MaterialTheme.colorScheme.primary
    val homeFallback = MaterialTheme.colorScheme.tertiary
    val (awayColor, homeColor) = remember(game.away.colors, game.home.colors, background) {
        selectComparisonColors(game.away.colors, game.home.colors, background, awayFallback, homeFallback)
    }
    // Legend: away on the left, home on the right, matching the header and the bars.
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(game.away, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(game.away.abbreviation, style = MaterialTheme.typography.labelLarge)
        ColorSwatch(awayColor, Modifier.padding(start = 6.dp))
        Spacer(Modifier.weight(1f))
        ColorSwatch(homeColor, Modifier.padding(end = 6.dp))
        Text(game.home.abbreviation, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        TeamBadge(game.home, size = 24.dp)
    }
    stats.forEach { StatRow(it, awayColor, homeColor) }
}

@Composable
private fun ColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(color))
}

@Composable
private fun StatRow(stat: TeamStat, awayColor: Color, homeColor: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatStat(stat.type, stat.away), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                stringResource(statLabel(stat.type)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                formatStat(stat.type, stat.home),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        val away = (stat.away as? StatValue.Number)?.value
        val home = (stat.home as? StatValue.Number)?.value
        if (away != null && home != null) {
            Spacer(Modifier.height(4.dp))
            StatBar(away, home, awayColor, homeColor)
        }
    }
}

/** Split bar showing each team's share of the stat. */
@Composable
private fun StatBar(away: Double, home: Double, awayColor: Color, homeColor: Color) {
    val total = away + home
    val awayShare = if (total > 0) (away / total).toFloat() else 0.5f
    Row(Modifier.fillMaxWidth().height(6.dp)) {
        Box(
            Modifier
                .weight(awayShare.coerceAtLeast(0.001f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(awayColor),
        )
        Spacer(Modifier.width(3.dp))
        Box(
            Modifier
                .weight((1f - awayShare).coerceAtLeast(0.001f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(homeColor),
        )
    }
}

private fun statLabel(type: TeamStatType): Int = when (type) {
    TeamStatType.SHOTS_ON_GOAL -> R.string.stat_shots_on_goal
    TeamStatType.FACEOFF_PERCENTAGE -> R.string.stat_faceoff_percentage
    TeamStatType.POWER_PLAY -> R.string.stat_power_play
    TeamStatType.PENALTY_MINUTES -> R.string.stat_penalty_minutes
    TeamStatType.HITS -> R.string.stat_hits
    TeamStatType.BLOCKED_SHOTS -> R.string.stat_blocked_shots
    TeamStatType.GIVEAWAYS -> R.string.stat_giveaways
    TeamStatType.TAKEAWAYS -> R.string.stat_takeaways
}

@Composable
private fun formatStat(type: TeamStatType, value: StatValue): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (value) {
        is StatValue.Text -> value.value
        is StatValue.Number -> if (type == TeamStatType.FACEOFF_PERCENTAGE) {
            NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(value.value)
        } else {
            NumberFormat.getIntegerInstance(locale).format(value.value)
        }
    }
}

// endregion

// region Mute

/** "I'm watching this one": turns off alerts and the Live Update for just this game. */
@Composable
private fun MuteGameRow(muted: Boolean, onMutedChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (muted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .toggleable(value = muted, role = Role.Switch, onValueChange = onMutedChange),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.game_mute)) },
            supportingContent = { Text(stringResource(if (muted) R.string.game_mute_on else R.string.game_mute_off)) },
            leadingContent = {
                Icon(if (muted) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive, contentDescription = null)
            },
            trailingContent = { Switch(checked = muted, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

// endregion

// region Where to watch

/**
 * TV and streaming broadcasts grouped as national (per country) and each team's local ones,
 * then each team's radio stream with a play button. Radio is offered until the game is over.
 */
@Composable
private fun WhereToWatchSection(details: GameDetails, game: Game) {
    val radio = details.radio.takeIf { game.status is GameStatus.Scheduled || game.status is GameStatus.Live }.orEmpty()
    if (details.broadcasts.isEmpty() && radio.isEmpty()) return
    SectionTitle(stringResource(R.string.game_section_watch))
    HorizontalDivider()
    val groups = details.broadcasts.groupBy { it.market to (if (it.market == BroadcastMarket.NATIONAL) it.countryCode else null) }
    val order = compareBy<Pair<BroadcastMarket, String?>>({ it.first.ordinal }, { it.second != "US" })
    groups.keys.sortedWith(order).forEach { key ->
        val (market, country) = key
        BroadcastGroup(
            title = when (market) {
                BroadcastMarket.NATIONAL -> when (country) {
                    "US" -> stringResource(R.string.watch_national_us)
                    "CA" -> stringResource(R.string.watch_national_ca)
                    else -> stringResource(R.string.watch_national)
                }
                BroadcastMarket.HOME -> stringResource(R.string.watch_local, game.home.name)
                BroadcastMarket.AWAY -> stringResource(R.string.watch_local, game.away.name)
            },
            broadcasts = groups.getValue(key),
        )
    }
    if (radio.isNotEmpty()) {
        val player = LocalRadioPlayer.current
        Text(
            stringResource(R.string.watch_radio),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        radio.forEach { stream ->
            val team = if (stream.side == TeamSide.HOME) game.home else game.away
            RadioRow(stream, team, game, player)
        }
    }
}

@Composable
private fun BroadcastGroup(title: String, broadcasts: List<Broadcast>) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    broadcasts.forEach { broadcast ->
        ListItem(
            headlineContent = { Text(BroadcastNames.displayName(broadcast.network)) },
            leadingContent = { BroadcastLogo(broadcast) },
        )
    }
}

/** The network's logo, or a TV icon when it has none (many don't). */
@Composable
private fun BroadcastLogo(broadcast: Broadcast) {
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val url = broadcast.logo?.forTheme(darkTheme)
    var failed by remember(url) { mutableStateOf(false) }
    Box(Modifier.size(width = 56.dp, height = 32.dp), contentAlignment = Alignment.Center) {
        if (url == null || failed) {
            Icon(Icons.Outlined.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { failed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RadioRow(stream: RadioStream, team: Team, game: Game, player: RadioPlayer) {
    val state = player.state
    val mediaId = radioMediaId(game.id, stream.side.name)
    val isThis = state.mediaId == mediaId
    val playing = isThis && state.isPlaying
    val paused = isThis && state.isPaused
    val title = stringResource(R.string.watch_radio_title, team.name)
    val subtitle = stringResource(R.string.game_matchup, game.away.abbreviation, game.home.abbreviation)
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            when {
                state.failedId == mediaId -> Text(stringResource(R.string.watch_radio_failed), color = MaterialTheme.colorScheme.error)
                playing && state.isBuffering -> Text(stringResource(R.string.watch_radio_connecting))
                playing -> Text(stringResource(R.string.watch_radio_playing))
                paused -> Text(stringResource(R.string.radio_paused))
                else -> {}
            }
        },
        leadingContent = { TeamBadge(team, size = 32.dp) },
        trailingContent = {
            FilledTonalIconButton(
                onClick = {
                    when {
                        playing -> player.stop()
                        paused -> player.resume()
                        else -> player.play(mediaId, stream.url, title, subtitle, team.logo?.forTheme(darkTheme = false))
                    }
                },
            ) {
                Icon(
                    if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.watch_radio_stop else R.string.watch_radio_listen, team.name),
                )
            }
        },
    )
}

// endregion

// region Game info

@Composable
private fun GameInfoSection(game: Game, today: LocalDate) {
    SectionTitle(stringResource(R.string.game_section_info))
    HorizontalDivider()
    val gameDate = game.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
    ListItem(
        headlineContent = { Text(game.league.name) },
        overlineContent = { Text(stringResource(R.string.game_league)) },
    )
    ListItem(
        headlineContent = { Text("${formatDate(gameDate, today)}, ${formatTime(game.startTime)}") },
        overlineContent = { Text(stringResource(R.string.game_start_time)) },
    )
    game.venue?.let { venue ->
        ListItem(
            headlineContent = { Text(venue) },
            overlineContent = { Text(stringResource(R.string.game_venue)) },
        )
    }
}

// endregion
