package io.rbware.zamboni.ui.scores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import kotlinx.coroutines.launch

/**
 * Sample list/detail screen.
 *
 * [NavigableListDetailPaneScaffold] shows one pane at a time on compact windows (with system back
 * returning from detail to list) and both panes side-by-side on medium/expanded windows, such as
 * tablets and unfolded foldables. Replace the placeholder items with real content.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ScoresScreen(modifier: Modifier = Modifier) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()
    val items = remember { (1..30).toList() }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                ItemList(
                    items = items,
                    selected = navigator.currentDestination?.contentKey,
                    onItemClick = { id ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                ItemDetail(
                    itemId = navigator.currentDestination?.contentKey,
                    showBack = navigator.canNavigateBack(),
                    onBack = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemList(
    items: List<Int>,
    selected: Int?,
    onItemClick: (Int) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_scores)) }) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(items, key = { it }) { id ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.scores_item_title, id)) },
                    supportingContent = { Text(stringResource(R.string.scores_item_subtitle)) },
                    colors = if (id == selected) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable { onItemClick(id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetail(
    itemId: Int?,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { itemId?.let { Text(stringResource(R.string.scores_item_title, it)) } },
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
        if (itemId == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(Modifier.padding(padding).padding(16.dp)) {
                Text(stringResource(R.string.detail_body, itemId), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
