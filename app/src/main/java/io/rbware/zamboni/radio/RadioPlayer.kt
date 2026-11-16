package io.rbware.zamboni.radio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** What the radio is doing, for the stream with [mediaId] (see [radioMediaId]). */
@Immutable
data class RadioState(
    val mediaId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** A stream is loaded but paused. */
    val isPaused: Boolean = false,
    /** The last stream failed to play. Cleared when another starts. */
    val failedId: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
) {
    /** A stream is loaded (playing or paused). */
    val hasStream get() = mediaId != null && (isPlaying || isPaused)

    /** The game the loaded stream belongs to. */
    val gameId: String? get() = mediaId?.let(::gameIdOfRadioMediaId)
}

/** Identifies one team's radio for one game, so the UI can tell which stream is loaded. */
fun radioMediaId(gameId: String, side: String) = "radio|$gameId|$side"

private fun gameIdOfRadioMediaId(mediaId: String): String? =
    mediaId.split('|').takeIf { it.size == 3 && it[0] == "radio" }?.get(1)

/** Controls the radio service from the UI. Connects while the calling composable is shown. */
class RadioPlayer internal constructor() {
    var state by mutableStateOf(RadioState())
        private set
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update(player)

        override fun onPlayerError(error: PlaybackException) {
            state = state.copy(failedId = controller?.currentMediaItem?.mediaId, isPlaying = false, isBuffering = false)
            controller?.clearMediaItems()
        }
    }

    internal fun attach(controller: MediaController) {
        this.controller = controller
        controller.addListener(listener)
        update(controller)
    }

    /** The controller itself is released with its future (see [rememberRadioPlayer]). */
    internal fun detach() {
        controller?.removeListener(listener)
        controller = null
    }

    private fun update(player: Player) {
        val item = player.currentMediaItem
        val loaded = item != null && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED
        state = state.copy(
            mediaId = item?.mediaId,
            isPlaying = loaded && player.playWhenReady,
            isPaused = loaded && !player.playWhenReady,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            title = item?.mediaMetadata?.title?.toString(),
            subtitle = item?.mediaMetadata?.artist?.toString(),
            artworkUrl = item?.mediaMetadata?.artworkUri?.toString(),
        )
    }

    fun pause() {
        controller?.pause()
    }

    /** Resumes at the live edge rather than where it was paused, since it's a live broadcast. */
    fun resume() {
        controller?.run {
            seekToDefaultPosition()
            play()
        }
    }

    /**
     * Plays [url] (an HLS stream). [id] identifies it for [state]; [title] and [subtitle] show in
     * the media notification, with [artworkUrl] as its picture.
     */
    fun play(id: String, url: String, title: String, subtitle: String, artworkUrl: String?) {
        val controller = controller ?: return
        state = state.copy(failedId = null)
        val item = MediaItem.Builder()
            .setMediaId(id)
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setArtworkUri(artworkUrl?.let(Uri::parse))
                    .setIsPlayable(true)
                    .build(),
            )
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
            .build()
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
    }
}

/** The app's radio player, provided by ZamboniApp so every screen controls the same stream. */
val LocalRadioPlayer = staticCompositionLocalOf<RadioPlayer> { error("No RadioPlayer provided") }

/** A [RadioPlayer] connected to the radio service for as long as the caller is composed. */
@Composable
fun rememberRadioPlayer(): RadioPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember { RadioPlayer() }
    DisposableEffect(context) {
        val future = MediaController.Builder(context, sessionToken(context)).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                player.attach(controller)
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            player.detach()
            MediaController.releaseFuture(future)
        }
    }
    return player
}

private fun sessionToken(context: Context) =
    SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
