package io.rbware.zamboni.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.ui.components.GroupedListHorizontalPadding
import io.rbware.zamboni.ui.components.TeamBadge

// Shared by onboarding's social step and Settings > Social accounts.

/** How many of a team's accounts are on. */
private enum class TeamState { NONE, SOME, ALL }

/**
 * NHL teams that have built-in accounts, favorites first. Tapping a team turns on all its
 * accounts, or turns them all off when they're already all on.
 */
@Composable
fun TeamAccountsRow(
    sources: List<SocialSource>,
    teams: List<Team>,
    favoriteTeamIds: Set<String>,
    isEnabled: (SocialSource) -> Boolean,
    onToggleTeam: (List<SocialSource>) -> Unit,
    hint: Int = R.string.social_teams_hint,
) {
    val accountsByTeam = remember(sources, teams, favoriteTeamIds) {
        val byCode = sources.filter { it.isDefault && it.teamCode != null }.groupBy { it.teamCode }
        teams.filter { it.league.id == Leagues.NHL.id && byCode.containsKey(it.abbreviation) }
            .sortedWith(compareBy({ it.id !in favoriteTeamIds }, { it.name }))
            .map { it to byCode.getValue(it.abbreviation) }
    }
    if (accountsByTeam.isEmpty()) return
    Column(Modifier.padding(top = 4.dp)) {
        Text(
            stringResource(R.string.social_teams_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = GroupedListHorizontalPadding + 16.dp),
        )
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = GroupedListHorizontalPadding + 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = GroupedListHorizontalPadding + 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(accountsByTeam, key = { (team, _) -> team.id }) { (team, accounts) ->
                val on = accounts.count(isEnabled)
                val state = when (on) {
                    0 -> TeamState.NONE
                    accounts.size -> TeamState.ALL
                    else -> TeamState.SOME
                }
                TeamAccountsChip(team, state, accounts.size, onClick = { onToggleTeam(accounts) })
            }
        }
    }
}

@Composable
private fun TeamAccountsChip(team: Team, state: TeamState, accountCount: Int, onClick: () -> Unit) {
    val stateText = stringResource(
        when (state) {
            TeamState.ALL -> R.string.social_team_all_on
            TeamState.SOME -> R.string.social_team_some_on
            TeamState.NONE -> R.string.social_team_off
        },
    )
    val description = pluralStringResource(R.plurals.social_team_accounts, accountCount, team.name, accountCount, stateText)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = state == TeamState.ALL, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (state == TeamState.ALL) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .then(
                        if (state == TeamState.SOME) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                    ),
            ) {
                TeamBadge(team, size = 36.dp)
            }
            if (state == TeamState.ALL) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Text(
            team.name.substringAfterLast(' ').takeIf { team.name.split(' ').size > 1 && it.length > 3 } ?: team.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
