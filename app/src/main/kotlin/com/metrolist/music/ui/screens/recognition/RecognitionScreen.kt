/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.RecognitionHistory
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.SearchRoutes
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    // Only reset in Ready state: Listening/Processing belong to a running widget-service
    // recognition that must not be cancelled; Success/NoMatch/Error are results pending
    // display and history saving.
    LaunchedEffect(Unit) {
        if (com.metrolist.music.recognition.MusicRecognitionService.recognitionStatus.value
                is RecognitionStatus.Ready
        ) {
            com.metrolist.music.recognition.MusicRecognitionService
                .reset()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.metrolist.music.recognition.MusicRecognitionService
                .reset()
        }
    }

    // Observe recognition status from service for real-time updates (Listening -> Processing -> Result)
    val recognitionStatus by com.metrolist.music.recognition.MusicRecognitionService.recognitionStatus
        .collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                coroutineScope.launch {
                    com.metrolist.music.recognition.MusicRecognitionService
                        .recognize(context)
                }
            }
        }

    fun startRecognition() {
        if (hasPermission) {
            coroutineScope.launch {
                com.metrolist.music.recognition.MusicRecognitionService
                    .recognize(context)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart &&
            com.metrolist.music.recognition.MusicRecognitionService.recognitionStatus.value
                is RecognitionStatus.Ready
        ) {
            startRecognition()
        }
    }

    fun resetToReady() {
        com.metrolist.music.recognition.MusicRecognitionService
            .reset()
    }

    fun saveToHistory(result: RecognitionResult) {
        // Skip if the widget service already persisted this result to avoid a duplicate entry
        if (com.metrolist.music.recognition.MusicRecognitionService.resultSavedExternally) return
        coroutineScope.launch(Dispatchers.IO) {
            database.query {
                insert(
                    RecognitionHistory(
                        trackId = result.trackId,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl,
                        coverArtHqUrl = result.coverArtHqUrl,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        label = result.label,
                        shazamUrl = result.shazamUrl,
                        appleMusicUrl = result.appleMusicUrl,
                        spotifyUrl = result.spotifyUrl,
                        isrc = result.isrc,
                        youtubeVideoId = result.youtubeVideoId,
                        recognizedAt = LocalDateTime.now(),
                    ),
                )
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.recognize_music)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("recognition_history") }) {
                        Icon(
                            painter = painterResource(R.drawable.history),
                            contentDescription = stringResource(R.string.recognition_history),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = recognitionStatus,
                transitionSpec = {
                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                },
                label = "recognition_content",
            ) { status ->
                when (status) {
                    is RecognitionStatus.Ready -> {
                        ReadyState(onStartRecognition = ::startRecognition)
                    }

                    is RecognitionStatus.Listening -> {
                        ListeningState(
                            onCancel = {
                                com.metrolist.music.recognition.MusicRecognitionService
                                    .reset()
                            },
                        )
                    }

                    is RecognitionStatus.Processing -> {
                        ProcessingState()
                    }

                    is RecognitionStatus.Success -> {
                        SuccessState(
                            result = status.result,
                            onPlayOnApp = { result ->
                                // Search for the track on YouTube Music
                                val searchQuery = "${result.title} ${result.artist}"
                                navController.navigate(SearchRoutes.resultRoute(searchQuery))
                            },
                            onTryAgain = {
                                startRecognition()
                            },
                            onClose = ::resetToReady,
                            onSaveToHistory = ::saveToHistory,
                        )
                    }

                    is RecognitionStatus.NoMatch -> {
                        NoMatchState(
                            message = status.message,
                            onTryAgain = {
                                startRecognition()
                            },
                        )
                    }

                    is RecognitionStatus.Error -> {
                        ErrorState(
                            message = status.message,
                            onTryAgain = {
                                startRecognition()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReadyState(onStartRecognition: () -> Unit) {
    val label = stringResource(R.string.tap_to_recognize)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        RecognitionOrb(label = label, onClick = onStartRecognition, modifier = Modifier.testTag("recognition-start"))
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.soun_recognition_ready_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ListeningState(onCancel: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "recognition pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "recognition pulse",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RecognitionOrb(label = stringResource(R.string.cancel), onClick = onCancel, pulse = pulse)
        Text(stringResource(R.string.listening), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.soun_recognition_listening_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(0.45f, 0.7f, 1f, 0.6f, 0.85f, 0.6f, 1f, 0.7f, 0.45f).forEachIndexed { index, height ->
                Box(Modifier.width(4.dp).height(24.dp * height)
                    .graphicsLayer { scaleY = if (index % 2 == 0) 0.55f + pulse * 0.45f else 1f - pulse * 0.45f }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)))
            }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.sounPanel(RoundedCornerShape(50))) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun RecognitionOrb(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, pulse: Float = 0f) {
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(246.dp).background(Brush.radialGradient(listOf(
            accent.copy(alpha = 0.25f), accent.copy(alpha = 0.09f), Color.Transparent)), CircleShape))
        Box(Modifier.size(228.dp).graphicsLayer {
            scaleX = 0.92f + 0.08f * pulse; scaleY = 0.92f + 0.08f * pulse
            alpha = 0.65f - 0.3f * pulse
        }.border(1.dp, accent.copy(alpha = 0.55f), CircleShape))
        Box(Modifier.size(204.dp).graphicsLayer {
            scaleX = 1f - 0.05f * pulse; scaleY = 1f - 0.05f * pulse
        }.border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape))
        Box(modifier.size(172.dp).sounPanel(CircleShape)
            .background(Brush.linearGradient(listOf(
                Color.White.copy(alpha = 0.20f), accent.copy(alpha = 0.12f), Color.Transparent)), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.mic), contentDescription = null,
                modifier = Modifier.size(62.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ProcessingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotate")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                ),
            label = "rotation",
        )

        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            brush =
                                Brush.sweepGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.primary,
                                        ),
                                ),
                            shape = CircleShape,
                        ),
            )

            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = stringResource(R.string.processing),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SuccessState(
    result: RecognitionResult,
    onPlayOnApp: (RecognitionResult) -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    onSaveToHistory: (RecognitionResult) -> Unit,
) {
    // Save to history when success is shown
    LaunchedEffect(result) {
        onSaveToHistory(result)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        // Album art
        Card(
            modifier =
                Modifier
                    .size(180.dp)
                    .aspectRatio(1f),
            shape = RoundedCornerShape(com.metrolist.music.constants.ThumbnailCornerRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            AsyncImage(
                model = result.coverArtHqUrl ?: result.coverArtUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Track info
        Text(
            text = result.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = result.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        result.album?.let { album ->
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons - stacked vertically
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = { onPlayOnApp(result) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.play_on_app))
            }

            FilledTonalButton(
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.re_listen))
            }

            // Close button - Material 3 Expressive outlined style
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun NoMatchState(
    message: String,
    onTryAgain: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        Text(
            text = stringResource(R.string.no_match_found),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Button(onClick = onTryAgain) {
            Icon(
                painter = painterResource(R.drawable.refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.try_again))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onTryAgain: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        Text(
            text = stringResource(R.string.recognition_error),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Button(onClick = onTryAgain) {
            Icon(
                painter = painterResource(R.drawable.refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.try_again))
        }
    }
}
