package io.rbware.zamboni.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.rbware.zamboni.BuildConfig
import io.rbware.zamboni.R
import io.rbware.zamboni.ui.components.GroupedListItem
import io.rbware.zamboni.ui.components.groupedItems

/** One About row; tapping it opens [url] when there is one. */
private data class AboutRow(val label: String, val value: String, val url: String? = null)

/** App name, icon, version, package, developer, license and source code. */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rows = listOf(
        AboutRow(
            stringResource(R.string.settings_version),
            stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        ),
        // The installed package, so debug builds show their ".debug" suffix.
        AboutRow(stringResource(R.string.about_package), context.packageName),
        AboutRow(stringResource(R.string.about_developer), stringResource(R.string.about_developer_name)),
        AboutRow(stringResource(R.string.about_license), stringResource(R.string.about_license_name), LICENSE_URL),
        AboutRow(stringResource(R.string.about_source), stringResource(R.string.about_source_value), SOURCE_URL),
    )
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(size = 96.dp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groupedItems(rows, key = { it.label }) { row, shape ->
            GroupedListItem(
                shape = shape,
                overlineContent = { Text(row.label) },
                headlineContent = { Text(row.value) },
                trailingContent = row.url?.let {
                    {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = row.url?.let { url -> Modifier.clickable { openLink(context, url) } } ?: Modifier,
            )
        }
    }
}

private const val SOURCE_URL = "https://github.com/RBWare/Zamboni-Android"
private const val LICENSE_URL = "$SOURCE_URL/blob/main/LICENSE"

/** Opens [url] in whichever app handles it (the GitHub app, if installed, or a browser). */
private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // No app can open links; nothing more to do.
    }
}

/**
 * The launcher icon, drawn from its layers: an adaptive icon can't be loaded as a painter.
 * The foreground is 108dp with the visible part in the middle 72dp, so it is drawn at 1.5x
 * the circle and cropped.
 */
@Composable
private fun AppIcon(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}
