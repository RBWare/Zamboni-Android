package io.rbware.zamboni.ui.news

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Opens [url] in a Custom Tab, falling back to any app that can view it. */
fun openArticle(context: Context, url: String) {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // No browser installed; nothing more we can do.
        }
    }
}
