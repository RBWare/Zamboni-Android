package io.rbware.zamboni.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * What the radio's artwork is drawn from: the team's logo (an SVG, made for dark backgrounds)
 * and main color.
 */
data class RadioArtwork(val logoUrl: String?, val color: Int?)

/**
 * Draws [artwork] as a square PNG for the media notification: the logo on a gradient of the
 * team's color. The system player can't load SVG logos itself, and uses this picture as its
 * background and to pick its colors. Null if nothing could be drawn.
 */
internal suspend fun renderRadioArtwork(context: Context, artwork: RadioArtwork): ByteArray? = try {
    val logo = artwork.logoUrl?.let { url ->
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(LOGO_SIZE)
            .allowHardware(false) // Drawn onto a software canvas below.
            .build()
        (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }
    withContext(Dispatchers.Default) { draw(logo, artwork.color ?: FALLBACK_COLOR) }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("RadioArtwork", "Couldn't draw the radio artwork: $e")
    null
}

private fun draw(logo: Bitmap?, color: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // A diagonal gradient from the team color to a darker shade of it.
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), color, darker(color), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
    logo?.let {
        // Fit the logo in the middle, keeping its proportions.
        val box = SIZE * LOGO_FRACTION
        val scale = minOf(box / it.width, box / it.height)
        val w = it.width * scale
        val h = it.height * scale
        val left = (SIZE - w) / 2
        val top = (SIZE - h) / 2
        canvas.drawBitmap(it, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

private fun darker(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[2] *= DARKEN
    return Color.HSVToColor(hsv)
}

private const val SIZE = 512
private const val LOGO_SIZE = 512
/** The logo's share of the picture's width and height. */
private const val LOGO_FRACTION = 0.6f
private const val DARKEN = 0.45f
private val FALLBACK_COLOR = Color.rgb(0x1E, 0x2A, 0x38)
