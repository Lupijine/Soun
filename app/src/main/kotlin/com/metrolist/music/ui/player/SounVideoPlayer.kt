/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ChunkedDataSource
import com.metrolist.music.ui.component.SounProgressBar
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors
import com.metrolist.music.utils.InnerTubeXPlayer
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.text.Normalizer

private const val VIDEO_MAX_HEIGHT = 720
private const val CONTROLS_HIDE_MS = 3_000L

/**
 * Soun video mode: plays the song's music video full screen with its own player, starting where
 * the song was. The song pauses meanwhile; closing hands the position back (for the same video id)
 * and resumes it, and reaching the end of the video moves the queue to the next song.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SounVideoPlayer(
    mediaMetadata: MediaMetadata,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mainPlayer = playerConnection.player

    val videoPlayer = remember { ExoPlayer.Builder(context).build() }
    val startPosition = remember { mainPlayer.currentPosition }
    val wasPlaying = remember { mainPlayer.playWhenReady }
    var sameVideoId by remember { mutableStateOf(true) }
    var endedNaturally by remember { mutableStateOf(false) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var aspect by remember { mutableFloatStateOf(16f / 9f) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    val noVideoText = stringResource(R.string.soun_video_not_found)

    DisposableEffect(Unit) {
        mainPlayer.pause()
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) loading = false
                if (playbackState == Player.STATE_ENDED) {
                    endedNaturally = true
                    onClose()
                }
            }

            override fun onPlayerError(playbackError: androidx.media3.common.PlaybackException) {
                loading = false
                error = playbackError.localizedMessage ?: noVideoText
            }
        }
        videoPlayer.addListener(listener)
        onDispose {
            val videoPosition = videoPlayer.currentPosition
            videoPlayer.removeListener(listener)
            videoPlayer.release()
            when {
                endedNaturally -> {
                    if (mainPlayer.hasNextMediaItem()) mainPlayer.seekToNextMediaItem()
                    mainPlayer.play()
                }
                else -> {
                    if (sameVideoId && videoPosition > 0) mainPlayer.seekTo(videoPosition)
                    if (wasPlaying) mainPlayer.play()
                }
            }
        }
    }

    LaunchedEffect(mediaMetadata.id) {
        val videoId = if (mediaMetadata.isVideoSong) mediaMetadata.id else findMusicVideo(mediaMetadata)
        if (videoId == null) {
            loading = false
            error = noVideoText
            return@LaunchedEffect
        }
        sameVideoId = videoId == mediaMetadata.id
        val streams = InnerTubeXPlayer.videoStreams(videoId, VIDEO_MAX_HEIGHT).getOrElse {
            loading = false
            error = noVideoText
            return@LaunchedEffect
        }
        if (streams.width != null && streams.height != null && streams.height > 0) {
            aspect = streams.width.toFloat() / streams.height
        }
        val http = OkHttpDataSource.Factory(OkHttpClient()).setDefaultRequestProperties(streams.headers)
        val factory = ProgressiveMediaSource.Factory(ChunkedDataSource.Factory(http))
        videoPlayer.setMediaSource(
            MergingMediaSource(
                factory.createMediaSource(MediaItem.fromUri(streams.videoUrl)),
                factory.createMediaSource(MediaItem.fromUri(streams.audioUrl)),
            ),
        )
        videoPlayer.prepare()
        if (sameVideoId) videoPlayer.seekTo(startPosition)
        videoPlayer.play()
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = videoPlayer.currentPosition
            duration = videoPlayer.duration.takeIf { it > 0 } ?: duration
            delay(250)
        }
    }

    LaunchedEffect(touches, playing) {
        controlsVisible = true
        if (playing) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (controlsVisible && playing) controlsVisible = false else touches++
            },
    ) {
        AndroidView(
            factory = { SurfaceView(it).also(videoPlayer::setVideoSurfaceView) },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.4f, 3f)),
        )

        if (loading && error == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        error?.let { message ->
            Text(
                text = message,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.sounGlass(CircleShape, sounGlassColors(), backdrop = null),
                    ) {
                        Icon(painterResource(R.drawable.expand_more), stringResource(R.string.close), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            mediaMetadata.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            mediaMetadata.artists.joinToString { it.name },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (error == null && !loading) {
                    IconButton(
                        onClick = {
                            touches++
                            if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(76.dp)
                            .sounGlass(CircleShape, sounGlassColors(), backdrop = null),
                    ) {
                        Icon(
                            painterResource(if (playing) R.drawable.pause else R.drawable.play),
                            stringResource(if (playing) R.string.pause else R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    SounProgressBar(
                        value = (scrubPosition ?: position).toFloat(),
                        valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                        onValueChange = {
                            touches++
                            scrubPosition = it.toLong()
                        },
                        onValueChangeFinished = {
                            scrubPosition?.let(videoPlayer::seekTo)
                            scrubPosition = null
                        },
                        color = Color.White,
                        enabled = duration > 0,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(makeTimeString(scrubPosition ?: position), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text(makeTimeString(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Songs from albums (ATV) are audio with a still picture; their music video is a separate
 * upload. Look it up by title and artist and accept it only when the video title contains the
 * song title and one of its artists matches.
 */
private suspend fun findMusicVideo(song: MediaMetadata): String? = withContext(Dispatchers.IO) {
    val artist = song.artists.firstOrNull()?.name.orEmpty()
    val results = YouTube.search("${song.title} $artist", YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
        ?.items?.filterIsInstance<SongItem>().orEmpty()
    val wantedTitle = song.title.normalizedForMatch()
    val wantedArtists = song.artists.map { it.name.normalizedForMatch() }.filter { it.isNotBlank() }
    results.firstOrNull { video ->
        val title = video.title.normalizedForMatch()
        val artists = video.artists.joinToString(" ") { it.name }.normalizedForMatch() + " " + title
        title.contains(wantedTitle) && wantedArtists.any { artists.contains(it) }
    }?.id
}

private fun String.normalizedForMatch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
