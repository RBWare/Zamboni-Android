package io.rbware.zamboni.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import io.rbware.zamboni.data.scores.GamesheetKeyCheck
import io.rbware.zamboni.data.scores.GamesheetKeySource

/** Short status for the Settings list. */
@Composable
fun gamesheetKeySummary(state: GamesheetKeyUiState): String = when (state.key.source) {
    GamesheetKeySource.USER -> stringResource(R.string.gamesheet_status_user, keySuffix(state.key.value))
    GamesheetKeySource.BUILT_IN -> stringResource(R.string.gamesheet_status_built_in)
    GamesheetKeySource.NONE -> stringResource(R.string.gamesheet_status_none)
}

/** The last few characters, so users can tell which key is saved without showing all of it. */
private fun keySuffix(key: String?): String = key.orEmpty().takeLast(4)

/** Lets the user enter, check and remove their Gamesheet partner key. */
@Composable
fun GamesheetKeySection(
    state: GamesheetKeyUiState,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showInput by rememberSaveable { mutableStateOf(false) }
    // Clear the field once a key has been accepted and saved.
    LaunchedEffect(state.lastCheck) {
        if (state.lastCheck != null && state.lastCheck != GamesheetKeyCheck.REJECTED) input = ""
    }
    val canSave = input.isNotBlank() && !state.checking

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.gamesheet_explanation), style = MaterialTheme.typography.bodyMedium)

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.gamesheet_current), style = MaterialTheme.typography.labelMedium)
                Text(gamesheetKeySummary(state), style = MaterialTheme.typography.bodyLarge)
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.gamesheet_key_label)) },
            singleLine = true,
            visualTransformation = if (showInput) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(input) }),
            trailingIcon = {
                IconButton(onClick = { showInput = !showInput }) {
                    Icon(
                        if (showInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (showInput) R.string.gamesheet_hide_key else R.string.gamesheet_show_key,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(input) }, enabled = canSave) {
                Text(stringResource(R.string.gamesheet_save))
            }
            if (state.key.source == GamesheetKeySource.USER) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.gamesheet_remove)) }
            }
            if (state.checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        state.lastCheck?.let { check ->
            Text(
                stringResource(
                    when (check) {
                        GamesheetKeyCheck.VALID -> R.string.gamesheet_check_valid
                        GamesheetKeyCheck.REJECTED -> R.string.gamesheet_check_rejected
                        GamesheetKeyCheck.FAILED -> R.string.gamesheet_check_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (check == GamesheetKeyCheck.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.key.source == GamesheetKeySource.USER && state.key.value != null) {
            Text(
                stringResource(R.string.gamesheet_remove_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
