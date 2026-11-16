package io.rbware.zamboni.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.notifications.canScheduleExactAlarms
import io.rbware.zamboni.ui.components.GroupedListHorizontalPadding
import io.rbware.zamboni.ui.components.GroupedListItem
import io.rbware.zamboni.ui.components.TonalIcon
import io.rbware.zamboni.ui.components.groupHeader
import io.rbware.zamboni.ui.components.groupedItemShape
import io.rbware.zamboni.ui.preferences.notificationTypeItems

/**
 * Notification settings: which alerts to get, a test notification, and a shortcut to the
 * system screen for sound and pop-up style.
 *
 * @param allowed whether the app may post at all; when false a banner explains how to fix it.
 */
@Composable
fun NotificationSettings(
    selected: Set<NotificationType>,
    onToggle: (NotificationType) -> Unit,
    allowed: Boolean,
    onAllow: () -> Unit,
    onSendTest: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var exactAlarms by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    LifecycleResumeEffect(Unit) {
        exactAlarms = canScheduleExactAlarms(context)
        onPauseOrDispose {}
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (!allowed) {
            item(key = "blocked") { NotificationsBlockedCard(onAllow) }
        }

        groupHeader(R.string.settings_notifications_alerts, key = "alerts")
        notificationTypeItems(selected, onToggle)
        // Exact alarms are what let Live Updates start at puck drop with the app closed.
        if (NotificationType.LIVE_UPDATES in selected && !exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "live-alarms") {
                GroupedListItem(
                    shape = groupedItemShape(0, 1),
                    headlineContent = { Text(stringResource(R.string.settings_live_alarms)) },
                    supportingContent = { Text(stringResource(R.string.settings_live_alarms_summary)) },
                    leadingContent = { TonalIcon(Icons.Outlined.Alarm) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            context.startSafely(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                            )
                        },
                )
            }
        }

        groupHeader(R.string.settings_notifications_more, key = "more")
        item(key = "test") {
            GroupedListItem(
                shape = groupedItemShape(0, 2),
                headlineContent = { Text(stringResource(R.string.settings_test_notification)) },
                supportingContent = { Text(stringResource(R.string.settings_test_notification_summary)) },
                leadingContent = { TonalIcon(Icons.Outlined.NotificationsActive) },
                modifier = Modifier.clickable(onClick = onSendTest),
            )
        }
        item(key = "system") {
            GroupedListItem(
                shape = groupedItemShape(1, 2),
                headlineContent = { Text(stringResource(R.string.settings_system_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_system_notifications_summary)) },
                leadingContent = { TonalIcon(Icons.Outlined.Tune) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenSystemSettings),
            )
        }

        item(key = "footer") {
            Text(
                stringResource(R.string.settings_notifications_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = GroupedListHorizontalPadding + 16.dp,
                    vertical = 16.dp,
                ),
            )
        }
    }
}

/** Shown when the permission is missing or notifications are turned off for the app. */
@Composable
fun NotificationsBlockedCard(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GroupedListHorizontalPadding, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_notifications_blocked_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_notifications_blocked_body), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onAllow, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.settings_notifications_allow))
                }
            }
        }
    }
}
