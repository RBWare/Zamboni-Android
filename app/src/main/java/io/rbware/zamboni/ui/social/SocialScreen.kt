package io.rbware.zamboni.ui.social

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.ui.components.MessageState
import io.rbware.zamboni.ui.news.openArticle

/**
 * Social tab: recent posts from the accounts the user follows, newest first. Posts open on
 * their platform (in a Custom Tab, or the platform's app if it handles the link).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialViewModel = viewModel(factory = SocialViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountPicker by rememberSaveable { mutableStateOf(false) }

    if (showAccountPicker) {
        AccountPickerSheet(
            sources = state.enabledSources.orEmpty(),
            selectedId = state.sourceFilter,
            onSelect = { id ->
                viewModel.setSourceFilter(id)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false },
        )
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfStale()
        onPauseOrDispose { }
    }

    // Name a few failed accounts; past that, a list of names would fill the snackbar.
    val failedMessage = if (state.failedSources.size > 3) {
        stringResource(R.string.social_many_sources_failed, state.failedSources.size)
    } else {
        stringResource(R.string.social_sources_failed, state.failedSources.joinToString(", ") { it.name })
    }
    LaunchedEffect(state.failedSources) {
        if (state.failedSources.isNotEmpty()) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.failedSourcesShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_social)) },
                actions = {
                    if (state.enabledSources.orEmpty().size > 1) {
                        IconButton(onClick = { showAccountPicker = true }) {
                            Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.social_filter_accounts))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SocialFilterBar(
                platforms = state.enabledPlatforms,
                platformFilter = state.platformFilter,
                selectedSource = state.selectedSource,
                onPlatformSelect = viewModel::setPlatformFilter,
                onClearSource = { viewModel.setSourceFilter(null) },
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val posts = state.visiblePosts
                when {
                    state.enabledSources?.isEmpty() == true -> MessageState(
                        message = stringResource(R.string.social_no_sources),
                        actionLabel = stringResource(R.string.social_choose_sources),
                        onAction = onOpenSettings,
                    )
                    posts == null && state.loadFailed -> MessageState(
                        message = stringResource(R.string.social_load_failed),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::refresh,
                    )
                    posts == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    posts.isEmpty() -> MessageState(stringResource(R.string.social_no_posts))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 360.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(posts, key = { it.id }) { post ->
                            PostCard(post, onClick = { openArticle(context, post.url) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Under the title: platform chips (All, Mastodon, YouTube) when more than one platform is on, or,
 * while one account is picked, a single chip for that account that clears the pick.
 */
@Composable
private fun SocialFilterBar(
    platforms: List<SocialPlatform>,
    platformFilter: SocialPlatform?,
    selectedSource: SocialSource?,
    onPlatformSelect: (SocialPlatform?) -> Unit,
    onClearSource: () -> Unit,
) {
    if (selectedSource == null && platforms.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selectedSource != null) {
            InputChip(
                selected = true,
                onClick = onClearSource,
                label = { Text(stringResource(R.string.social_name_with_platform, selectedSource.name, selectedSource.platform.displayName)) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.social_clear_filter),
                        modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    )
                },
            )
        } else {
            PlatformChip(stringResource(R.string.social_all_sources), selected = platformFilter == null) { onPlatformSelect(null) }
            platforms.forEach { platform ->
                PlatformChip(platform.displayName, selected = platformFilter == platform) {
                    onPlatformSelect(if (platformFilter == platform) null else platform)
                }
            }
        }
    }
}

@Composable
private fun PlatformChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** Searchable list of the accounts that are on, by platform; picking one shows only its posts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    sources: List<SocialSource>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(sources, query) {
        val q = query.trim()
        sources.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.handle.contains(q, ignoreCase = true) }
            .sortedWith(compareBy({ it.platform.ordinal }, { it.name.lowercase() }))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.social_search_accounts)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            if (query.isBlank()) {
                item(key = "all") {
                    AccountRow(
                        title = stringResource(R.string.social_all_sources),
                        subtitle = null,
                        selected = selectedId == null,
                        onClick = { onSelect(null) },
                    )
                }
            }
            matches.groupBy { it.platform }.forEach { (platform, accounts) ->
                item(key = "header-${platform.name}") {
                    Text(
                        platform.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(accounts, key = { it.id }) { source ->
                    AccountRow(
                        title = source.name,
                        subtitle = source.handle,
                        selected = source.id == selectedId,
                        onClick = { onSelect(source.id) },
                    )
                }
            }
            if (matches.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.social_search_no_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}
