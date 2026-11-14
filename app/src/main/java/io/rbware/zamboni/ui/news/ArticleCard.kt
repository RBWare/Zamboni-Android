package io.rbware.zamboni.ui.news

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.NewsArticle

@Composable
fun ArticleCard(article: NewsArticle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = article.publishedAt?.let {
                        stringResource(R.string.news_meta, article.source.name, relativeTime(it.toEpochMilli()))
                    } ?: article.source.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                article.summary?.let { summary ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            article.imageUrl?.let { url ->
                var failed by remember(url) { mutableStateOf(false) }
                if (!failed) {
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onState = { failed = it is AsyncImagePainter.State.Error },
                        modifier = Modifier
                            .size(96.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

/** "5 minutes ago", "3 hours ago", "Yesterday", ... */
private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
