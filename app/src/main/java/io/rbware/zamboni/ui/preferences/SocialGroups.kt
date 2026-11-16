package io.rbware.zamboni.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.SocialCategory
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.ui.components.GroupedListHorizontalPadding

/** A titled group of built-in accounts. */
data class SocialGroup(@param:StringRes val title: Int, val key: String, val sources: List<SocialSource>)

/**
 * Splits [platform]'s built-in accounts into groups: the ones on by default ("Featured") first,
 * then the rest by category. Empty groups are left out.
 */
fun builtInSocialGroups(sources: List<SocialSource>, platform: SocialPlatform): List<SocialGroup> {
    val builtIn = sources.filter { it.platform == platform && it.isDefault }
    val (featured, others) = builtIn.partition { it.enabledByDefault }
    val byCategory = others.groupBy { it.category ?: SocialCategory.OTHER }
    return buildList {
        if (featured.isNotEmpty()) add(SocialGroup(R.string.social_group_featured, "${platform.name}-featured", featured))
        SocialCategory.entries.forEach { category ->
            byCategory[category]?.let { add(SocialGroup(category.title, "${platform.name}-${category.name}", it)) }
        }
    }
}

@get:StringRes
val SocialCategory.title: Int
    get() = when (this) {
        SocialCategory.LEAGUE -> R.string.social_group_leagues
        SocialCategory.TEAM -> R.string.social_group_teams
        SocialCategory.REPORTER -> R.string.social_group_reporters
        SocialCategory.MEDIA -> R.string.social_group_media
        SocialCategory.PERSONALITY -> R.string.social_group_personalities
        SocialCategory.OTHER -> R.string.social_group_other
    }

/** Switches between platforms, so each platform's (possibly long) list is shown on its own. */
@Composable
fun PlatformSelector(
    platforms: List<SocialPlatform>,
    selected: SocialPlatform,
    onSelect: (SocialPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GroupedListHorizontalPadding, vertical = 8.dp),
    ) {
        platforms.forEachIndexed { index, platform ->
            SegmentedButton(
                selected = platform == selected,
                onClick = { onSelect(platform) },
                shape = SegmentedButtonDefaults.itemShape(index, platforms.size),
            ) {
                Text(platform.displayName)
            }
        }
    }
}
