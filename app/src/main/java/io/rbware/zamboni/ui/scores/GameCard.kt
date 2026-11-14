package io.rbware.zamboni.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.ui.components.TeamBadge

@Composable
fun GameCard(
    game: Game,
    favoriteTeamIds: Set<String>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = if (selected) {
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamLine(game, game.away, game.awayScore, game.away.id in favoriteTeamIds)
                TeamLine(game, game.home, game.homeScore, game.home.id in favoriteTeamIds)
            }
            VerticalDivider(Modifier.height(56.dp).padding(horizontal = 12.dp))
            GameStatusColumn(game, Modifier.width(68.dp))
        }
    }
}

@Composable
private fun TeamLine(game: Game, team: Team, score: Int?, favorite: Boolean) {
    val winner = game.winner
    val lost = winner != null && winner != team
    val emphasis = if (lost) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(team, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        // The name (plus favorite star) takes all space between the logo and the score, so it
        // only truncates when it truly doesn't fit, and the score column lines up across rows.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (winner == team) FontWeight.SemiBold else null,
                color = emphasis,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.scores_favorite_team),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
        }
        if (score != null) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (winner == team || game.status is GameStatus.Live) FontWeight.Bold else null,
                color = emphasis,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 8.dp).widthIn(min = ScoreMinWidth),
            )
        }
    }
}

/** Fixed minimum so single- and double-digit scores stay right-aligned in the same column. */
private val ScoreMinWidth = 24.dp

@Composable
private fun GameStatusColumn(game: Game, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (game.status is GameStatus.Live) {
            LiveBadge()
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = gameStatusText(game),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (game.status is GameStatus.Live) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.status_live),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
