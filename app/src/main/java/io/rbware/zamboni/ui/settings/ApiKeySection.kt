package io.rbware.zamboni.ui.settings

import androidx.annotation.StringRes
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
import io.rbware.zamboni.data.scores.ApiKeyCheck
import io.rbware.zamboni.data.scores.ApiKeySource

/** The texts that differ between data sources. */
data class ApiKeyTexts(
    @param:StringRes val explanation: Int,
    @param:StringRes val keyLabel: Int,
    @param:StringRes val statusBuiltIn: Int,
    @param:StringRes val statusNone: Int,
    @param:StringRes val checkValid: Int,
    @param:StringRes val checkRejected: Int,
    @param:StringRes val checkFailed: Int,
    @param:StringRes val removeHint: Int,
) {
    companion object {
        val Gamesheet = ApiKeyTexts(
            explanation = R.string.gamesheet_explanation,
            keyLabel = R.string.gamesheet_key_label,
            statusBuiltIn = R.string.gamesheet_status_built_in,
            statusNone = R.string.gamesheet_status_none,
            checkValid = R.string.gamesheet_check_valid,
            checkRejected = R.string.gamesheet_check_rejected,
            checkFailed = R.string.gamesheet_check_failed,
            removeHint = R.string.gamesheet_remove_hint,
        )

        val HockeyTech = ApiKeyTexts(
            explanation = R.string.hockeytech_explanation,
            keyLabel = R.string.hockeytech_key_label,
            statusBuiltIn = R.string.hockeytech_status_built_in,
            statusNone = R.string.hockeytech_status_none,
            checkValid = R.string.hockeytech_check_valid,
            checkRejected = R.string.hockeytech_check_rejected,
            checkFailed = R.string.hockeytech_check_failed,
            removeHint = R.string.hockeytech_remove_hint,
        )
    }
}

/** Short status for the Settings list. */
@Composable
fun apiKeySummary(state: ApiKeyUiState, texts: ApiKeyTexts): String = when (state.key.source) {
    ApiKeySource.USER -> stringResource(R.string.api_key_status_user, keySuffix(state.key.value))
    ApiKeySource.BUILT_IN -> stringResource(texts.statusBuiltIn)
    ApiKeySource.NONE -> stringResource(texts.statusNone)
}

/** The last few characters, so users can tell which key is saved without showing all of it. */
private fun keySuffix(key: String?): String = key.orEmpty().takeLast(4)

/** Lets the user enter, check and remove their own key for a data source. */
@Composable
fun ApiKeySection(
    state: ApiKeyUiState,
    texts: ApiKeyTexts,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showInput by rememberSaveable { mutableStateOf(false) }
    // Clear the field once a key has been accepted and saved.
    LaunchedEffect(state.lastCheck) {
        if (state.lastCheck != null && state.lastCheck != ApiKeyCheck.REJECTED) input = ""
    }
    val canSave = input.isNotBlank() && !state.checking

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(texts.explanation), style = MaterialTheme.typography.bodyMedium)

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.api_key_current), style = MaterialTheme.typography.labelMedium)
                Text(apiKeySummary(state, texts), style = MaterialTheme.typography.bodyLarge)
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(texts.keyLabel)) },
            singleLine = true,
            visualTransformation = if (showInput) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(input) }),
            trailingIcon = {
                IconButton(onClick = { showInput = !showInput }) {
                    Icon(
                        if (showInput) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (showInput) R.string.api_key_hide else R.string.api_key_show,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(input) }, enabled = canSave) {
                Text(stringResource(R.string.api_key_save))
            }
            if (state.key.source == ApiKeySource.USER) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.api_key_remove)) }
            }
            if (state.checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        state.lastCheck?.let { check ->
            Text(
                stringResource(
                    when (check) {
                        ApiKeyCheck.VALID -> texts.checkValid
                        ApiKeyCheck.REJECTED -> texts.checkRejected
                        ApiKeyCheck.FAILED -> texts.checkFailed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (check == ApiKeyCheck.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.key.source == ApiKeySource.USER && state.key.value != null) {
            Text(
                stringResource(texts.removeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
