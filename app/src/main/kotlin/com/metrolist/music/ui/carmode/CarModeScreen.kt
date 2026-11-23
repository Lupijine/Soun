/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.carmode

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.SounGlassColors
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.utils.SounHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

private val CarBase = Color(0xFF09080D)

// Car mode is always dark, whatever the app theme, so its glass is tuned here rather than taken from the theme.
private val CarGlass =
    SounGlassColors(
        fillTop = Color.White.copy(alpha = 0.16f),
        fillBottom = Color.White.copy(alpha = 0.04f),
        rimBright = Color.White.copy(alpha = 0.42f),
        rimDim = Color.White.copy(alpha = 0.08f),
        shadow = Color.Black.copy(alpha = 0.35f),
        selectedPill = SounAccent.copy(alpha = 0.22f),
        surfaceTint = Color.Black.copy(alpha = 0.10f),
        fallback = Color(0xFF221F2A).copy(alpha = 0.85f),
    )

private val CarAccentGlass =
    CarGlass.copy(
        fillTop = SounAccent.copy(alpha = 0.46f),
        fillBottom = SounAccent.copy(alpha = 0.22f),
        rimBright = Color.White.copy(alpha = 0.55f),
        surfaceTint = SounAccent.copy(alpha = 0.10f),
        fallback = Color(0xFF3A3360).copy(alpha = 0.92f),
    )

/** Full-screen driving view: covers the whole app, including the navigation bar and mini player. */
@Composable
fun CarModeScreen(
    playerConnection: PlayerConnection,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window.setDimAmount(0f)
                window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes =
                        window.attributes.apply {
                            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                }
                // Reach under the status and navigation bars; the content pads itself with the safe insets.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.attributes = window.attributes.apply { fitInsetsTypes = 0 }
                }
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        CarModeContent(playerConnection, onClose)
    }
}

@Composable
private fun CarModeContent(
    playerConnection: PlayerConnection,
    onClose: () -> Unit,
) {
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val dragOffset = remember { Animatable(0f) }

    val isEpisode = currentSong?.song?.isEpisode == true
    val liked = if (isEpisode) currentSong?.song?.inLibrary != null else currentSong?.song?.liked == true

    val actions =
        CarActions(
            previous = {
                SounHaptics.touch(view)
                playerConnection.seekToPrevious()
            },
            next = {
                SounHaptics.touch(view)
                playerConnection.seekToNext()
            },
            playPause = {
                SounHaptics.touch(view)
                val player = runCatching { playerConnection.player }.getOrNull()
                if (playbackState == Player.STATE_ENDED && player != null) {
                    player.seekTo(0, 0)
                    player.playWhenReady = true
                } else {
                    playerConnection.togglePlayPause()
                }
            },
            like = {
                SounHaptics.confirm(view)
                playerConnection.toggleLike()
            },
            close = onClose,
        )

    val state =
        CarNowPlaying(
            title = mediaMetadata?.title ?: stringResource(R.string.soun_car_mode_nothing_playing),
            artist = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
            isPlaying = isPlaying,
            liked = liked,
            canSkipNext = canSkipNext,
        )

    Box(Modifier.fillMaxSize().background(CarBase)) {
        // Only the background is recorded for the glass; the buttons are siblings so they never sample themselves.
        CarBackground(
            thumbnailUrl = state.thumbnailUrl,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (isRenderEffectSupported()) Modifier.layerBackdrop(backdrop) else Modifier),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .carSwipe(
                    offset = dragOffset,
                    onSwipe = { forward ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (forward) playerConnection.seekToNext() else playerConnection.seekToPrevious()
                    },
                    launch = { block -> scope.launch { block() } },
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            val swipeShift = Modifier.graphicsLayer {
                translationX = dragOffset.value
                alpha = 1f - (abs(dragOffset.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
            }
            if (maxWidth > maxHeight) {
                CarLandscape(state, actions, backdrop, maxWidth, maxHeight, swipeShift)
            } else {
                CarPortrait(state, actions, backdrop, maxWidth, maxHeight, swipeShift)
            }
        }
    }
}

private class CarActions(
    val previous: () -> Unit,
    val next: () -> Unit,
    val playPause: () -> Unit,
    val like: () -> Unit,
    val close: () -> Unit,
)

private class CarNowPlaying(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val isPlaying: Boolean,
    val liked: Boolean,
    val canSkipNext: Boolean,
)

@Composable
private fun CarPortrait(
    state: CarNowPlaying,
    actions: CarActions,
    backdrop: Backdrop,
    maxWidth: Dp,
    maxHeight: Dp,
    swipeShift: Modifier,
) {
    val playSize = (maxWidth * 0.38f).coerceIn(120.dp, 156.dp)
    val sideSize = ((maxWidth - playSize) / 2 - 12.dp).coerceIn(96.dp, 116.dp)
    Column(Modifier.fillMaxSize()) {
        CarCloseButton(actions.close, backdrop)
        Spacer(Modifier.height(16.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).fillMaxWidth().then(swipeShift),
        ) {
            CarCover(state.thumbnailUrl, Modifier.aspectRatio(1f))
        }
        Spacer(Modifier.height(24.dp))
        CarTitleRow(state, actions, backdrop, swipeShift)
        Spacer(Modifier.height(if (maxHeight > 720.dp) 40.dp else 24.dp))
        CarControls(state, actions, backdrop, playSize, sideSize, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CarLandscape(
    state: CarNowPlaying,
    actions: CarActions,
    backdrop: Backdrop,
    maxWidth: Dp,
    maxHeight: Dp,
    swipeShift: Modifier,
) {
    val playSize = min(maxHeight * 0.44f, maxWidth * 0.2f).coerceIn(120.dp, 160.dp)
    val sideSize = (playSize * 0.78f).coerceIn(96.dp, 124.dp)
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxHeight()) {
            CarCloseButton(actions.close, backdrop)
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.TopStart, modifier = Modifier.weight(1f).then(swipeShift)) {
                CarCover(
                    state.thumbnailUrl,
                    Modifier.widthIn(max = maxWidth * 0.45f).aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
            }
        }
        Spacer(Modifier.width(32.dp))
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            CarTitleRow(state, actions, backdrop, swipeShift)
            CarControls(
                state,
                actions,
                backdrop,
                playSize,
                sideSize,
                Modifier.widthIn(max = playSize + sideSize * 2 + 64.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CarTitleRow(
    state: CarNowPlaying,
    actions: CarActions,
    backdrop: Backdrop,
    swipeShift: Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).then(swipeShift)) {
            Text(
                text = state.title,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.artist.isNotEmpty()) {
                Text(
                    text = state.artist,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        CarGlassButton(
            icon = if (state.liked) R.drawable.favorite else R.drawable.favorite_border,
            contentDescription = stringResource(if (state.liked) R.string.soun_car_mode_unlike else R.string.soun_car_mode_like),
            size = 96.dp,
            iconSize = 44.dp,
            backdrop = backdrop,
            iconTint = if (state.liked) SounAccent else Color.White,
            onClick = actions.like,
        )
    }
}

@Composable
private fun CarControls(
    state: CarNowPlaying,
    actions: CarActions,
    backdrop: Backdrop,
    playSize: Dp,
    sideSize: Dp,
    modifier: Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        CarGlassButton(
            icon = R.drawable.skip_previous,
            contentDescription = stringResource(R.string.previous),
            size = sideSize,
            iconSize = sideSize * 0.42f,
            backdrop = backdrop,
            onClick = actions.previous,
        )
        CarGlassButton(
            icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
            contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
            size = playSize,
            iconSize = playSize * 0.4f,
            backdrop = backdrop,
            colors = CarAccentGlass,
            onClick = actions.playPause,
        )
        CarGlassButton(
            icon = R.drawable.skip_next,
            contentDescription = stringResource(R.string.next),
            size = sideSize,
            iconSize = sideSize * 0.42f,
            backdrop = backdrop,
            enabled = state.canSkipNext,
            onClick = actions.next,
        )
    }
}

@Composable
private fun CarCloseButton(
    onClose: () -> Unit,
    backdrop: Backdrop,
) {
    CarGlassButton(
        icon = R.drawable.close,
        contentDescription = stringResource(R.string.soun_car_mode_exit),
        size = 80.dp,
        iconSize = 38.dp,
        backdrop = backdrop,
        onClick = onClose,
    )
}

@Composable
private fun CarGlassButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    size: Dp,
    iconSize: Dp,
    backdrop: Backdrop,
    onClick: () -> Unit,
    colors: SounGlassColors = CarGlass,
    enabled: Boolean = true,
    iconTint: Color = Color.White,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "carButtonScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else 0.4f
                }
                .sounGlass(CircleShape, colors, elevation = 14f, backdrop = backdrop)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CarCover(
    thumbnailUrl: String?,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f)),
    ) {
        if (thumbnailUrl == null) {
            Icon(
                painter = painterResource(R.drawable.soun_car),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(0.4f),
            )
        } else {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CarBackground(
    thumbnailUrl: String?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    Box(modifier.background(CarBase)) {
        Crossfade(targetState = thumbnailUrl, animationSpec = tween(800), label = "carBackground") { url ->
            if (url != null) {
                AsyncImage(
                    // A tiny image scaled up already reads as a blur where Modifier.blur is unavailable (before Android 12).
                    model = ImageRequest.Builder(context).data(url).size(64, 64).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(72.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.50f), Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.80f)),
                    ),
                ),
        )
    }
}

/** Swiping left or right anywhere skips to the next or previous song. */
private fun Modifier.carSwipe(
    offset: Animatable<Float, AnimationVector1D>,
    onSwipe: (forward: Boolean) -> Unit,
    launch: (suspend () -> Unit) -> Unit,
): Modifier =
    pointerInput(Unit) {
        val threshold = 72.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) > threshold) onSwipe(total < 0)
                launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
            },
            onDragCancel = { launch { offset.animateTo(0f) } },
            onHorizontalDrag = { change, amount ->
                change.consume()
                total += amount
                launch { offset.snapTo(total * 0.4f) }
            },
        )
    }
