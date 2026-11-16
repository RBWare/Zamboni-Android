package io.rbware.zamboni.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.rbware.zamboni.R
import io.rbware.zamboni.radio.RadioPlayer

/**
 * The game whose details are on screen, if any. GameDetail sets it, so the now-playing bar can
 * step aside while that game's own radio controls are visible.
 */
class ShownGame {
    var gameId by mutableStateOf<String?>(null)
}

val LocalShownGame = compositionLocalOf { ShownGame() }

/**
 * A slim bar above the navigation while team radio is playing or paused: what's on, play/pause,
 * and stop. Tapping it opens the game. Hidden while that game's details are on screen.
 */
@Composable
fun NowPlayingBar(
    player: RadioPlayer,
    shownGameId: String?,
    onOpenGame: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Add room for the system navigation bar (when no bottom navigation bar is below this). */
    padForSystemBar: Boolean = false,
) {
    val state = player.state
    val visible = state.hasStream && state.gameId != shownGameId
    AnimatedVisibility(visible, modifier = modifier, enter = expandVertically(), exit = shrinkVertically()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(if (padForSystemBar) Modifier.navigationBarsPadding() else Modifier) {
                if (state.isPlaying && state.isBuffering) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.radio_open_game)) {
                            state.gameId?.let(onOpenGame)
                        }
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                        if (state.artworkUrl != null) {
                            AsyncImage(
                                model = state.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Icon(Icons.Outlined.Radio, contentDescription = null)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                state.subtitle,
                                stringResource(if (state.isPaused) R.string.radio_paused else R.string.radio_live),
                            ).joinToString(" - "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalIconButton(onClick = { if (state.isPaused) player.resume() else player.pause() }) {
                        Icon(
                            if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = stringResource(if (state.isPaused) R.string.radio_resume else R.string.radio_pause),
                        )
                    }
                    IconButton(onClick = player::stop) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.radio_stop))
                    }
                }
            }
        }
    }
}
