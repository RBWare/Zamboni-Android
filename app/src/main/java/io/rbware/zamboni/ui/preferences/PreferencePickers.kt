package io.rbware.zamboni.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.ui.components.TeamBadge

/*
 * Selection UIs shared by onboarding and Settings so both always look and behave the same.
 * Each one scrolls by itself, so place it in a container with bounded height.
 */

/** Favorite-team grid grouped by league. Columns adapt to the window width (2 on phones, more on tablets). */
@Composable
fun TeamPicker(
    teams: List<Team>?,
    selectedIds: Set<String>,
    onToggle: (Team) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    if (teams == null) {
        LoadingBox(modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Group by league with a full-width header per league, in the order the teams arrive.
        teams.groupBy { it.league }.forEach { (league, leagueTeams) ->
            item(key = "header-${league.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    text = league.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(leagueTeams, key = { it.id }, contentType = { "team" }) { team ->
                TeamCard(team = team, selected = team.id in selectedIds, onClick = { onToggle(team) })
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamBadge(team = team, highlighted = selected)
            Spacer(Modifier.width(12.dp))
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                )
            }
        }
    }
}

/** On/off switch for each [NotificationType]. */
@Composable
fun NotificationPicker(
    selected: Set<NotificationType>,
    onToggle: (NotificationType) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(NotificationType.entries, key = { it.name }) { type ->
            val checked = type in selected
            ListItem(
                headlineContent = { Text(stringResource(type.label)) },
                supportingContent = { Text(stringResource(type.description)) },
                trailingContent = { Switch(checked = checked, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle(type) }),
            )
        }
    }
}

/** Checkbox list of available RSS sources. */
@Composable
fun NewsSourcePicker(
    sources: List<NewsSource>?,
    selectedIds: Set<String>,
    onToggle: (NewsSource) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
) {
    if (sources == null) {
        LoadingBox(modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(sources, key = { it.id }) { source ->
            val checked = source.id in selectedIds
            ListItem(
                headlineContent = { Text(source.name) },
                supportingContent = sourceHost(source.feedUrl)?.let { host -> { Text(host) } },
                leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(source) }),
            )
        }
    }
}

/** "thehockeywriters.com" from a feed URL, to show where a source comes from. */
private fun sourceHost(feedUrl: String): String? =
    runCatching { java.net.URI(feedUrl).host?.removePrefix("www.") }.getOrNull()

@Composable
private fun LoadingBox(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
