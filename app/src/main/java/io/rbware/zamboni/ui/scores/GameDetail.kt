package io.rbware.zamboni.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.ui.components.TeamBadge
import java.time.LocalDate
import java.time.ZoneId

/**
 * Game detail pane. Shows the score header and basic info for now; box score, goals and stats
 * will come from the hockeynerd.com API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetail(
    game: Game?,
    today: LocalDate,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    game?.let {
                        Text(stringResource(R.string.game_matchup, it.away.abbreviation, it.home.abbreviation))
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (game == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.scores_select_game), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)) {
                ScoreHeader(game)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                val gameDate = game.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                ListItem(
                    headlineContent = { Text(game.league.name) },
                    overlineContent = { Text(stringResource(R.string.game_league)) },
                )
                ListItem(
                    headlineContent = {
                        Text("${formatDate(gameDate, today)}, ${formatTime(game.startTime)}")
                    },
                    overlineContent = { Text(stringResource(R.string.game_start_time)) },
                )
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.game_more_coming),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreHeader(game: Game) {
    // Top-aligned so both logos sit on the same line and both names start on the same line,
    // even when one name wraps to two lines.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        HeaderTeam(game.away, Modifier.weight(1f))
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
        HeaderTeam(game.home, Modifier.weight(1f))
    }
}

private val HeaderLogoSize = 64.dp

@Composable
private fun HeaderTeam(team: Team, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(team, size = HeaderLogoSize)
        Spacer(Modifier.height(8.dp))
        Text(team.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
}
