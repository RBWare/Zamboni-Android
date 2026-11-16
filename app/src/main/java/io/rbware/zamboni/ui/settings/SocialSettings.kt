package io.rbware.zamboni.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.ui.components.GroupedListHorizontalPadding
import io.rbware.zamboni.ui.components.GroupedListItem
import io.rbware.zamboni.ui.components.TonalIcon
import io.rbware.zamboni.ui.components.groupHeader
import io.rbware.zamboni.ui.components.groupedItemShape
import io.rbware.zamboni.ui.components.groupedItems
import io.rbware.zamboni.ui.preferences.builtInSocialGroups
import io.rbware.zamboni.ui.preferences.PlatformSelector
import io.rbware.zamboni.ui.preferences.SwitchRow

/**
 * Social accounts by platform. Each platform starts with the user's own accounts and an
 * "Add account" row, then the built-in accounts (featured first, then by category). Built-in
 * accounts can only be turned on or off; the user's own can also be removed.
 */
@Composable
fun SocialSourcesSettings(
    sources: List<SocialSource>?,
    isEnabled: (SocialSource) -> Boolean,
    onToggle: (SocialSource) -> Unit,
    onRemove: (SocialSource) -> Unit,
    onAdd: (SocialPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sources == null) return
    var platform by rememberSaveable { mutableStateOf(SocialPlatform.entries.first()) }
    Column(modifier.fillMaxSize()) {
        PlatformSelector(SocialPlatform.entries, platform, onSelect = { platform = it })
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val custom = sources.filter { it.platform == platform && !it.isDefault }
            // The "add" row closes the group, so it counts toward the group's shape.
            val count = custom.size + 1
            groupHeader(R.string.social_group_yours, key = "yours-${platform.name}")
            custom.forEachIndexed { index, source ->
                item(key = source.id) {
                    CustomSourceRow(
                        source = source,
                        shape = groupedItemShape(index, count),
                        enabled = isEnabled(source),
                        onToggle = { onToggle(source) },
                        onRemove = { onRemove(source) },
                    )
                }
            }
            item(key = "add-${platform.name}") {
                GroupedListItem(
                    shape = groupedItemShape(count - 1, count),
                    headlineContent = { Text(stringResource(addAccountTitle(platform))) },
                    leadingContent = { TonalIcon(Icons.Outlined.Add) },
                    modifier = Modifier.clickable { onAdd(platform) },
                )
            }

            builtInSocialGroups(sources, platform).forEach { group ->
                groupHeader(group.title, key = group.key)
                groupedItems(group.sources, key = { it.id }) { source, shape ->
                    SwitchRow(
                        shape = shape,
                        title = source.name,
                        subtitle = source.handle,
                        checked = isEnabled(source),
                        onToggle = { onToggle(source) },
                    )
                }
            }
            item(key = "footer") {
                Text(
                    stringResource(R.string.social_settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = GroupedListHorizontalPadding + 16.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun CustomSourceRow(
    source: SocialSource,
    shape: Shape,
    enabled: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    GroupedListItem(
        shape = shape,
        headlineContent = { Text(source.name) },
        supportingContent = { Text(source.handle) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.social_remove_account, source.name))
                }
                Switch(checked = enabled, onCheckedChange = null)
            }
        },
        modifier = Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = { onToggle() }),
    )
}

private fun addAccountTitle(platform: SocialPlatform): Int = when (platform) {
    SocialPlatform.MASTODON -> R.string.social_add_mastodon
    SocialPlatform.YOUTUBE -> R.string.social_add_youtube
}

/** Platform-specific wording for the add dialog. */
private class AddAccountText(val label: Int, val placeholder: Int, val hint: Int, val invalid: Int, val notFound: Int)

private fun addAccountText(platform: SocialPlatform) = when (platform) {
    SocialPlatform.MASTODON -> AddAccountText(
        label = R.string.social_add_mastodon_label,
        placeholder = R.string.social_add_mastodon_placeholder,
        hint = R.string.social_add_mastodon_hint,
        invalid = R.string.social_add_error_invalid,
        notFound = R.string.social_add_error_not_found,
    )
    SocialPlatform.YOUTUBE -> AddAccountText(
        label = R.string.social_add_youtube_label,
        placeholder = R.string.social_add_youtube_placeholder,
        hint = R.string.social_add_youtube_hint,
        invalid = R.string.social_add_youtube_error_invalid,
        notFound = R.string.social_add_youtube_error_not_found,
    )
}

/** Asks for an account address, then checks it exists before adding it. */
@Composable
fun AddAccountDialog(
    state: AddAccountUiState,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable(state.platform) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val canAdd = input.isNotBlank() && !state.checking
    val text = addAccountText(state.platform)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(addAccountTitle(state.platform))) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(text.label)) },
                placeholder = { Text(stringResource(text.placeholder)) },
                supportingText = {
                    Text(
                        stringResource(
                            when (state.error) {
                                AddAccountError.INVALID -> text.invalid
                                AddAccountError.ALREADY_ADDED -> R.string.social_add_error_exists
                                AddAccountError.NOT_FOUND -> text.notFound
                                AddAccountError.UNREACHABLE -> R.string.social_add_error_unreachable
                                null -> text.hint
                            },
                        ),
                    )
                },
                isError = state.error != null,
                singleLine = true,
                enabled = !state.checking,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd(input) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(input) }, enabled = canAdd) {
                if (state.checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.social_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
