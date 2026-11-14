package io.rbware.zamboni.ui.scores

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.rbware.zamboni.data.model.TeamColors
import kotlin.math.sqrt

/** WCAG minimum contrast for non-text graphics such as bars. */
private const val MIN_GRAPHIC_CONTRAST = 3f

/** Colors closer than this (RGB distance, 0-441) are treated as the same color. */
private const val MIN_COLOR_DISTANCE = 80f

/**
 * Picks a color for each team to use in side-by-side comparisons (e.g. stat bars) that is
 * visible on [background] and distinguishable from the other team's.
 *
 * Each team prefers its primary color, then its secondary. A color that doesn't reach 3:1
 * contrast with [background] is skipped; if neither does, the better one is lightened or
 * darkened until it does. If the home color ends up too close to the away color, the home
 * team switches to its other color. Teams without colors use the fallbacks.
 */
fun selectComparisonColors(
    away: TeamColors?,
    home: TeamColors?,
    background: Color,
    awayFallback: Color,
    homeFallback: Color,
): Pair<Color, Color> {
    val awayColor = visibleCandidates(away, background).firstOrNull() ?: awayFallback
    val homeCandidates = visibleCandidates(home, background).ifEmpty { listOf(homeFallback) }
    val homeColor = homeCandidates.firstOrNull { distance(it, awayColor) >= MIN_COLOR_DISTANCE }
        ?: homeCandidates.first()
    return awayColor to homeColor
}

/** The team's colors in preference order, each made visible on [background]. */
private fun visibleCandidates(colors: TeamColors?, background: Color): List<Color> {
    val raw = listOfNotNull(colors?.primary, colors?.secondary).map { Color(it) }
    if (raw.isEmpty()) return emptyList()
    val passing = raw.filter { contrast(it, background) >= MIN_GRAPHIC_CONTRAST }
    if (passing.isNotEmpty()) return passing
    // Nothing passes as-is: adjust each color toward whichever extreme contrasts with the
    // background, keeping the order of preference.
    return raw.map { adjustForContrast(it, background) }
}

private fun adjustForContrast(color: Color, background: Color): Color {
    val target = if (background.luminance() < 0.5f) Color.White else Color.Black
    var fraction = 0f
    var adjusted = color
    while (contrast(adjusted, background) < MIN_GRAPHIC_CONTRAST && fraction < 1f) {
        fraction += 0.1f
        adjusted = lerp(color, target, fraction.coerceAtMost(1f))
    }
    return adjusted
}

/** WCAG contrast ratio between two colors, 1 to 21. */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private fun distance(a: Color, b: Color): Float {
    val dr = (a.red - b.red) * 255
    val dg = (a.green - b.green) * 255
    val db = (a.blue - b.blue) * 255
    return sqrt(dr * dr + dg * dg + db * db)
}
