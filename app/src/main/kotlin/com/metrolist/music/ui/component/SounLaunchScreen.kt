package com.metrolist.music.ui.component

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.R
import kotlinx.coroutines.delay

@Composable
fun SounLaunchScreen(ready: Boolean, onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    var ended by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val video = remember(context) { LaunchVideoView(context) { ended = true } }
    BackHandler { finish() }
    LaunchedEffect(ended, ready) { if (ended && ready) finish() }
    // A slow network or an unavailable decoder must never trap the user at launch.
    LaunchedEffect(Unit) { delay(6_500); finish() }
    DisposableEffect(video, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            video.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        video.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            video.release()
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF0B0408)).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val width = minOf(maxWidth, maxHeight * (9f / 16f))
        AndroidView(factory = { video }, modifier = Modifier.size(width, width * (16f / 9f)))
    }
}

private class LaunchVideoView(context: Context, private val onEnded: () -> Unit) :
    TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var output: Surface? = null
    private var prepared = false
    private var active = false
    private var ended = false

    init { surfaceTextureListener = this }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (ended) return
        try {
            output = Surface(texture)
            val decoder = MediaPlayer()
            player = decoder
            decoder.apply {
                context.resources.openRawResourceFd(R.raw.soun_launch).use { asset ->
                    setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
                }
                setSurface(output)
                setVolume(0f, 0f)
                isLooping = false
                setOnPreparedListener {
                    prepared = true
                    if (active) it.start()
                }
                setOnCompletionListener { complete() }
                setOnErrorListener { _, _, _ -> complete(); true }
                prepareAsync()
            }
        } catch (_: Exception) {
            release()
            complete()
        }
    }

    fun setActive(value: Boolean) {
        active = value
        if (!prepared || ended) return
        if (value) player?.start() else player?.pause()
    }

    private fun complete() {
        if (ended) return
        ended = true
        onEnded()
    }

    fun release() {
        prepared = false
        player?.setOnPreparedListener(null)
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        player?.release()
        player = null
        output?.release()
        output = null
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
}
