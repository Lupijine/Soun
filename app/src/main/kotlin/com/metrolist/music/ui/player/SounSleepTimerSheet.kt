/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.constants.SleepTimerDefaultKey
import com.metrolist.music.constants.SleepTimerFadeOutKey
import com.metrolist.music.constants.SleepTimerStopAfterCurrentSongKey
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.SleepStop
import com.metrolist.music.ui.component.sounLavenderInk
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.SounGlassDialog
import com.metrolist.music.ui.utils.SounHaptics
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.safeDataStoreEdit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val OnAccent = Color(0xFF17121F)
private val PRESETS = listOf(15, 30, 45, 60, 90)
private const val MIN_MINUTES = 5
private const val MAX_MINUTES = 180

/**
 * The sleep timer: a time (presets, or −/+ in 5-minute steps) or the end of this song, this
 * part of a long track or this album, with a fade-out. While it runs it shows a countdown ring,
 * when the music will stop, and lets you add time or turn it off.
 */
@Composable
fun SounSleepTimerSheet(
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    showNotice: (String) -> Unit,
) {
    val timer = playerConnection.service.sleepTimer ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val active = timer.isActive

    val defaultMinutes by rememberPreference(SleepTimerDefaultKey, 30f)
    var minutes by remember { mutableIntStateOf(defaultMinutes.roundToInt().coerceIn(MIN_MINUTES, MAX_MINUTES)) }
    var fadeOut by rememberPreference(SleepTimerFadeOutKey, false)
    var finishSong by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val defaultSetTemplate = stringResource(R.string.sleep_timer_default_set)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val chapterState by playerConnection.chapterState.collectAsState()
    val hasChapters = chapterState.chapters.isNotEmpty() && chapterState.mediaId == mediaMetadata?.id
    val hasAlbum = mediaMetadata?.album != null

    // Ticks once a second for the countdown and the "time left" labels.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    SounGlassDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SounAccent.copy(alpha = 0.16f)),
                ) {
                    Icon(painterResource(R.drawable.bedtime), contentDescription = null, tint = sounLavenderInk(), modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (active) R.string.sleep_timer_active else R.string.sleep_timer),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        stringResource(if (active) R.string.soun_sleep_running_subtitle else R.string.soun_sleep_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (active) {
                // Running: countdown ring, when it stops, add time, turn off.
                val remaining = timer.remainingMs() ?: 0L
                val total = timer.armedTotalMs.coerceAtLeast(remaining).coerceAtLeast(1L)
                @Suppress("UNUSED_EXPRESSION") now
                Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.CenterHorizontally).size(190.dp)) {
                    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        drawArc(
                            SounAccent,
                            -90f,
                            360f * (remaining.toFloat() / total).coerceIn(0f, 1f),
                            false,
                            topLeft,
                            arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            makeTimeString(remaining),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp,
                            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            when {
                                timer.stopAt == SleepStop.CHAPTER -> stringResource(R.string.soun_sleep_at_end_part)
                                timer.stopAt == SleepStop.ALBUM -> stringResource(R.string.soun_sleep_at_end_album)
                                timer.pauseWhenSongEnd -> stringResource(R.string.soun_sleep_at_end_song)
                                else -> stringResource(R.string.soun_sleep_stops_at, clockIn(remaining))
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (timer.fadeOutEnabled) {
                    Text(
                        stringResource(R.string.sleep_timer_fade_out_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = sounLavenderInk(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                if (timer.triggerTime != -1L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        listOf(5, 15).forEach { add ->
                            SecondaryPill(stringResource(R.string.soun_sleep_add, add)) {
                                SounHaptics.tick(view)
                                timer.extend(add)
                                now = System.currentTimeMillis()
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryPill(stringResource(R.string.sleep_timer_turn_off), Modifier.weight(1f), danger = true) {
                        SounHaptics.confirm(view)
                        timer.clear()
                        onDismiss()
                    }
                    PrimaryPill(stringResource(R.string.soun_sleep_done), null, Modifier.weight(1f), onDismiss)
                }
            } else {
                // Set up: a time…
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RoundButton(R.drawable.remove, stringResource(R.string.soun_sleep_less)) {
                        SounHaptics.tick(view)
                        minutes = (minutes - 5).coerceAtLeast(MIN_MINUTES)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            minutes.toString(),
                            fontSize = 56.sp,
                            lineHeight = 58.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.6).sp,
                            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            pluralStringResource(R.plurals.soun_sleep_minutes_unit, minutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RoundButton(R.drawable.add, stringResource(R.string.soun_sleep_more)) {
                        SounHaptics.tick(view)
                        minutes = (minutes + 5).coerceAtMost(MAX_MINUTES)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    PRESETS.forEach { preset ->
                        val selected = preset == minutes
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) SounAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                                    .clickable {
                                        SounHaptics.tick(view)
                                        minutes = preset
                                    },
                        ) {
                            Text(
                                preset.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (selected) OnAccent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.soun_sleep_stops_at, clockIn(minutes * 60_000L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                // …or the end of something.
                Text(
                    stringResource(R.string.soun_sleep_or_end_of),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val player = playerConnection.player
                    @Suppress("UNUSED_EXPRESSION") now
                    val songLeft = (player.duration - player.currentPosition).takeIf { player.duration > 0 }
                    EndOption(
                        icon = R.drawable.music_note,
                        title = stringResource(R.string.soun_sleep_this_song),
                        detail = songLeft?.let { stringResource(R.string.soun_card_resume_left, makeTimeString(it)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        timer.startAtEndOf(SleepStop.SONG, fadeOut)
                        onDismiss()
                    }
                    if (hasChapters) {
                        val partEnd = playerConnection.service.currentChapterEndMs()
                        EndOption(
                            icon = R.drawable.list,
                            title = stringResource(R.string.soun_sleep_this_part),
                            detail = partEnd?.let { stringResource(R.string.soun_card_resume_left, makeTimeString((it - player.currentPosition).coerceAtLeast(0))) },
                            modifier = Modifier.weight(1f),
                        ) {
                            timer.startAtEndOf(SleepStop.CHAPTER, fadeOut, playerConnection.service.currentChapterEndMs())
                            onDismiss()
                        }
                    }
                    if (hasAlbum) {
                        EndOption(
                            icon = R.drawable.album,
                            title = stringResource(R.string.soun_sleep_this_album),
                            detail = mediaMetadata?.album?.title,
                            modifier = Modifier.weight(1f),
                        ) {
                            timer.startAtEndOf(SleepStop.ALBUM, fadeOut)
                            onDismiss()
                        }
                    }
                }

                ToggleRow(stringResource(R.string.soun_sleep_fade), stringResource(R.string.soun_sleep_fade_desc), fadeOut) { fadeOut = it }
                ToggleRow(stringResource(R.string.soun_sleep_finish_song), stringResource(R.string.soun_sleep_finish_song_desc), finishSong) { finishSong = it }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val isDefault = minutes == defaultMinutes.roundToInt()
                    SecondaryPill(
                        stringResource(if (isDefault) R.string.soun_sleep_is_default else R.string.set_as_default),
                        Modifier.weight(1f),
                    ) {
                        if (!isDefault) {
                            scope.launch { context.safeDataStoreEdit { it[SleepTimerDefaultKey] = minutes.toFloat() } }
                            showNotice(String.format(defaultSetTemplate, minutes))
                        }
                    }
                    PrimaryPill(stringResource(R.string.soun_sleep_start, minutes), R.drawable.bedtime, Modifier.weight(1.4f)) {
                        SounHaptics.confirm(view)
                        timer.start(minute = minutes, stopAfterCurrentSong = finishSong, fadeOut = fadeOut)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** The wall-clock time [fromNowMs] from now, in the phone's format ("23:40", "11:40 PM"). */
private fun clockIn(fromNowMs: Long): String =
    LocalTime.now().plusSeconds(fromNowMs / 1000).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun EndOption(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = sounLavenderInk(), modifier = Modifier.size(20.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onChange(!checked) }
                .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PrimaryPill(
    text: String,
    @DrawableRes icon: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(SounAccent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
        Text(text, color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SecondaryPill(
    text: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(50))
                .background(color.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable(onClickLabel = label, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp))
    }
}
