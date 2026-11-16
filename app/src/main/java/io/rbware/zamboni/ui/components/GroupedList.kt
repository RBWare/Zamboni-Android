package io.rbware.zamboni.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/*
 * Grouped lists in the Material 3 settings style: rows in a group sit on rounded "segments"
 * with small gaps between them; the group's outer corners are rounder than the inner ones.
 * Put them on a [MaterialTheme.colorScheme.surfaceContainer] background so the segments stand out.
 */

private val OuterCorner = 20.dp
private val InnerCorner = 4.dp
private val SegmentGap = 2.dp

/** Horizontal inset of grouped rows and headers from the screen edge. */
val GroupedListHorizontalPadding = 16.dp

/** Shape for row [index] of a group of [count]: round outer corners, tight inner ones. */
fun groupedItemShape(index: Int, count: Int): Shape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Section title above a group. */
fun LazyListScope.groupHeader(title: String, key: Any = "header-$title") {
    item(key = key, contentType = "group-header") {
        GroupHeader(title)
    }
}

/** Section title above a group, from a string resource. */
fun LazyListScope.groupHeader(@StringRes title: Int, key: Any = "header-$title") {
    item(key = key, contentType = "group-header") {
        GroupHeader(stringResource(title))
    }
}

@Composable
fun GroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = GroupedListHorizontalPadding + 16.dp,
            end = GroupedListHorizontalPadding + 16.dp,
            top = 20.dp,
            bottom = 8.dp,
        ),
    )
}

/** A group of rows built from [items]; [row] receives each item and the shape to draw it with. */
fun <T> LazyListScope.groupedItems(
    items: List<T>,
    key: (T) -> Any,
    row: @Composable (item: T, shape: Shape) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> key(item) }, contentType = { _, _ -> "grouped-row" }) { index, item ->
        row(item, groupedItemShape(index, items.size))
    }
}

/**
 * One row of a group. Pass click or toggle behavior through [modifier]; it's applied inside
 * the rounded shape so the ripple follows the corners.
 */
@Composable
fun GroupedListItem(
    shape: Shape,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceBright
    ListItem(
        headlineContent = headlineContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GroupedListHorizontalPadding)
            .padding(bottom = SegmentGap)
            .clip(shape)
            .background(container)
            .then(modifier),
    )
}

/** Leading icon on a tonal circle, as used by the top-level settings rows. */
@Composable
fun TonalIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}
