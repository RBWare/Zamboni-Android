package io.rbware.zamboni.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.rbware.zamboni.data.model.Team

/**
 * The team's logo in the variant matching the current theme, or a circular abbreviation badge
 * when the team has no logo or it fails to load. Logos come from the app-wide Coil disk cache
 * (filled by TeamLogoPrefetcher), so they are normally shown without a network request.
 */
@Composable
fun TeamBadge(
    team: Team,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    highlighted: Boolean = false,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // Decide light vs dark from the actual theme colors rather than the system setting,
        // so it stays correct if the app ever gets its own theme override.
        val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val logoUrl = team.logo?.forTheme(darkTheme)
        var logoFailed by remember(logoUrl) { mutableStateOf(false) }
        if (logoUrl == null || logoFailed) {
            AbbreviationBadge(team, size, highlighted)
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { logoFailed = it is AsyncImagePainter.State.Error },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AbbreviationBadge(team: Team, size: Dp, highlighted: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                team.abbreviation,
                style = if (size >= 48.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
