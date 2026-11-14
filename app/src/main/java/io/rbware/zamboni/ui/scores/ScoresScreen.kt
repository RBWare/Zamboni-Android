package io.rbware.zamboni.ui.scores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.League
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Scores tab: pick a date, filter by league and favorites, and open a game.
 *
 * Uses a list/detail layout: on phones a game opens full-screen; on tablets and unfolded
 * foldables the game appears beside the list.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ScoresScreen(
    modifier: Modifier = Modifier,
    viewModel: ScoresViewModel = viewModel(factory = ScoresViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                ScoresListPane(
                    state = state,
                    selectedGameId = navigator.currentDestination?.contentKey,
                    actions = ScoresActions(
                        onGameClick = { id ->
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                        },
                        onPreviousDay = viewModel::previousDay,
                        onNextDay = viewModel::nextDay,
                        onToday = viewModel::goToToday,
                        onDateSelected = viewModel::selectDate,
                        onRefresh = viewModel::refresh,
                        onRefreshErrorShown = viewModel::refreshErrorShown,
                        onToggleLeague = { viewModel.toggleLeague(it) },
                        onShowAllLeagues = { viewModel.showAllLeagues() },
                        onFavoritesOnlyChange = { viewModel.setFavoritesOnly(it) },
                        onFavoritesFirstChange = { viewModel.setFavoritesFirst(it) },
                        onClearFilters = { viewModel.clearFilters() },
                    ),
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val gameId = navigator.currentDestination?.contentKey
                GameDetail(
                    game = state.games?.firstOrNull { it.id == gameId },
                    today = state.today,
                    showBack = navigator.canNavigateBack(),
                    onBack = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
    )
}

private class ScoresActions(
    val onGameClick: (String) -> Unit,
    val onPreviousDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onToday: () -> Unit,
    val onDateSelected: (LocalDate) -> Unit,
    val onRefresh: () -> Unit,
    val onRefreshErrorShown: () -> Unit,
    val onToggleLeague: (League) -> Unit,
    val onShowAllLeagues: () -> Unit,
    val onFavoritesOnlyChange: (Boolean) -> Unit,
    val onFavoritesFirstChange: (Boolean) -> Unit,
    val onClearFilters: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoresListPane(
    state: ScoresUiState,
    selectedGameId: String?,
    actions: ScoresActions,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val refreshErrorMessage = stringResource(R.string.scores_refresh_failed)
    LaunchedEffect(state.refreshFailed) {
        if (state.refreshFailed) {
            snackbarHostState.showSnackbar(refreshErrorMessage)
            actions.onRefreshErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_scores)) },
                actions = {
                    if (!state.isToday) {
                        IconButton(onClick = actions.onToday) {
                            Icon(Icons.Filled.Today, contentDescription = stringResource(R.string.scores_go_to_today))
                        }
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.scores_choose_date))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scores_favorites_first)) },
                                // Favorites-only already hides everything else, so ordering doesn't apply.
                                enabled = !state.filter.favoritesOnly,
                                trailingIcon = {
                                    Checkbox(
                                        checked = state.filter.favoritesFirst,
                                        onCheckedChange = null,
                                        enabled = !state.filter.favoritesOnly,
                                    )
                                },
                                onClick = {
                                    actions.onFavoritesFirstChange(!state.filter.favoritesFirst)
                                    showMenu = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            DateSelector(
                date = state.date,
                today = state.today,
                onPrevious = actions.onPreviousDay,
                onNext = actions.onNextDay,
                onPick = { showDatePicker = true },
            )
            FilterRow(state, actions)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val games = state.games
                when {
                    games == null && state.loadFailed -> MessageState(
                        message = stringResource(R.string.scores_load_failed),
                        actionLabel = stringResource(R.string.retry),
                        onAction = actions.onRefresh,
                    )
                    games == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    games.isEmpty() -> MessageState(stringResource(R.string.scores_no_games))
                    state.sections.isEmpty() -> MessageState(
                        message = stringResource(R.string.scores_no_matching_games),
                        actionLabel = stringResource(R.string.scores_clear_filters),
                        onAction = actions.onClearFilters,
                    )
                    else -> GameList(state, selectedGameId, actions.onGameClick)
                }
            }
        }
    }

    if (showDatePicker) {
        ScoresDatePicker(
            initial = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                showDatePicker = false
                actions.onDateSelected(it)
            },
        )
    }
}

@Composable
private fun DateSelector(
    date: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    val relative = relativeDayLabel(date, today)
    val formatted = formatDate(date, today)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.scores_previous_day))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = stringResource(R.string.scores_choose_date), onClick = onPick)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(relative ?: formatted, style = MaterialTheme.typography.titleMedium)
            if (relative != null) {
                Text(formatted, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.scores_next_day))
        }
    }
}

@Composable
private fun FilterRow(state: ScoresUiState, actions: ScoresActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "favorites") {
            val selected = state.filter.favoritesOnly
            FilterChip(
                selected = selected,
                onClick = { actions.onFavoritesOnlyChange(!selected) },
                label = { Text(stringResource(R.string.scores_favorites_only)) },
                leadingIcon = {
                    Icon(
                        if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        item(key = "divider") { VerticalDivider(Modifier.height(24.dp)) }
        item(key = "all") {
            val selected = state.filter.leagueIds.isEmpty()
            FilterChip(
                selected = selected,
                onClick = actions.onShowAllLeagues,
                label = { Text(stringResource(R.string.scores_all_leagues)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
        items(state.leagues, key = { it.id }) { league ->
            val selected = league.id in state.filter.leagueIds
            FilterChip(
                selected = selected,
                onClick = { actions.onToggleLeague(league) },
                label = { Text(league.shortName) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/** Game cards in an adaptive grid: one column on phones, more when the pane is wide. */
@Composable
private fun GameList(state: ScoresUiState, selectedGameId: String?, onGameClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.sections.forEach { section ->
            item(key = section.kind.key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                SectionHeader(section)
            }
            items(section.games, key = { it.id }, contentType = { "game" }) { game ->
                GameCard(
                    game = game,
                    favoriteTeamIds = state.favoriteTeamIds,
                    selected = game.id == selectedGameId,
                    onClick = { onGameClick(game.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(section: GameSection) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val kind = section.kind) {
            GameSection.Kind.Favorites -> {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp),
                )
                Text(
                    stringResource(R.string.scores_section_favorites),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is GameSection.Kind.ByLeague -> Text(
                kind.league.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            pluralStringResource(R.plurals.scores_game_count, section.games.size, section.games.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Centered message with an optional action. Scrollable so pull-to-refresh still works. */
@Composable
private fun MessageState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(96.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoresDatePicker(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // The Material date picker works in UTC milliseconds at midnight.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
