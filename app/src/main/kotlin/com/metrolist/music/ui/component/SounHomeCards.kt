/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.DayPeriod
import com.metrolist.music.utils.DiscoverPage
import com.metrolist.music.utils.DriftedSongs
import com.metrolist.music.utils.OnThisDayRange
import com.metrolist.music.utils.OnThisDayWindow
import com.metrolist.music.utils.SavedMoment
import com.metrolist.music.utils.SounReleaseWatcher
import com.metrolist.music.utils.StreakSummary
import com.metrolist.music.utils.TimeOfDayMix
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.viewmodels.HomeViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/* Shared pieces: the same glass panel and type as "Your week". ---------------------------- */

private val OnAccent = Color(0xFF17121F)
private val CardShape = RoundedCornerShape(28.dp)

@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxWidth()
                .sounPanel(CardShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(18.dp),
        content = content,
    )
}

@Composable
private fun CardHeader(
    title: String,
    trailingText: String? = null,
    showArrow: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showArrow) {
            Icon(
                painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Cover(
    url: String?,
    size: Dp,
    radius: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes fallback: Int = R.drawable.music_note,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Icon(
            painterResource(fallback),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(minOf(size / 3, 28.dp)),
        )
        if (url != null) {
            AsyncImage(
                model = url.resize(360, 360),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PrimaryPill(
    text: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .background(SounAccent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = OnAccent, modifier = Modifier.size(18.dp))
        Text(text, color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SecondaryPill(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun RoundIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable(onClickLabel = label, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Chip(text: String, accent: Boolean = false) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (accent) sounLavenderInk() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (accent) SounAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = sounLavenderInk(),
        maxLines = 1,
    )
}

private val Song.artistNames: String get() = artists.joinToString { it.name }

/* 1 · Pick up where you left off ------------------------------------------------------------ */

@Composable
fun ResumeHomeCard(
    card: HomeViewModel.ResumeCard,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = card.song.song.duration * 1000L
    HomeCard(modifier) {
        CardHeader(stringResource(R.string.soun_card_resume_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(card.song.song.thumbnailUrl, 84.dp, 16.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(card.song.song.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(card.song.artistNames, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.chapterCount > 0 && card.chapterIndex >= 0) {
                    val part = stringResource(R.string.soun_card_resume_part, card.chapterIndex + 1, card.chapterCount)
                    Chip(card.chapterTitle?.let { "$part · $it" } ?: part)
                }
            }
        }
        if (durationMs > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fraction = (card.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SounAccent),
                    )
                }
                Row {
                    Text(makeTimeString(card.positionMs), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.soun_card_resume_left, makeTimeString((durationMs - card.positionMs).coerceAtLeast(0))),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryPill(
                stringResource(R.string.soun_card_resume_at, makeTimeString(card.positionMs)),
                R.drawable.play,
                onResume,
                Modifier.weight(1f),
            )
            SecondaryPill(stringResource(R.string.soun_start_over), onStartOver)
        }
    }
}

/* 2 · Listening streak ---------------------------------------------------------------------- */

@Composable
fun StreakHomeCard(
    streak: StreakSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier, onClick = onClick) {
        CardHeader(
            stringResource(R.string.soun_card_streak_title),
            trailingText = pluralStringResource(R.plurals.soun_card_streak_best, streak.best, streak.best),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SounAccent.copy(alpha = 0.16f)),
            ) {
                Icon(painterResource(R.drawable.soun_streak), contentDescription = null, tint = sounLavenderInk(), modifier = Modifier.size(28.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        streak.current.toString(),
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.2).sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pluralStringResource(R.plurals.soun_card_streak_days, streak.current),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(
                    if (streak.listenedToday) {
                        stringResource(R.string.soun_card_streak_today_done)
                    } else {
                        stringResource(R.string.soun_card_streak_keep, streak.current + 1)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val today = remember { LocalDate.now() }
        val labels =
            remember(today) {
                List(streak.lastDays.size) { i ->
                    today.minusDays((streak.lastDays.size - 1 - i).toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                }
            }
        streak.lastDays.chunked(7).forEachIndexed { row, week ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEachIndexed { column, listened ->
                    val index = row * 7 + column
                    val isToday = index == streak.lastDays.lastIndex
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (listened) SounAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                                .then(
                                    if (isToday && !listened) {
                                        Modifier.border(1.5.dp, SounAccent, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(
                            labels[index],
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isToday) sounLavenderInk() else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/* 3 · On this day --------------------------------------------------------------------------- */

@Composable
fun OnThisDayHomeCard(
    windows: List<OnThisDayWindow>,
    onPlay: (OnThisDayWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (windows.isEmpty()) return
    var picked by rememberSaveable { mutableStateOf(windows.last().range) }
    val window = windows.firstOrNull { it.range == picked } ?: windows.last()
    HomeCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.soun_card_on_this_day_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (windows.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(3.dp),
                ) {
                    windows.forEach { option ->
                        val selected = option.range == window.range
                        Text(
                            stringResource(option.range.shortLabel()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable { picked = option.range }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(window.topSong.song.thumbnailUrl, 112.dp, 18.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Eyebrow(stringResource(window.range.longLabel()))
                Text(window.topSong.song.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(window.topSong.artistNames, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.soun_card_on_this_day_plays, window.topSongPlays, window.topSongPlays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryPill(stringResource(R.string.soun_card_on_this_day_play), R.drawable.play, { onPlay(window) }, Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.soun_card_songs, window.songs.size, window.songs.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun OnThisDayRange.shortLabel(): Int =
    when (this) {
        OnThisDayRange.MONTH -> R.string.soun_card_range_month_short
        OnThisDayRange.HALF_YEAR -> R.string.soun_card_range_half_short
        OnThisDayRange.YEAR -> R.string.soun_card_range_year_short
    }

private fun OnThisDayRange.longLabel(): Int =
    when (this) {
        OnThisDayRange.MONTH -> R.string.soun_card_range_month
        OnThisDayRange.HALF_YEAR -> R.string.soun_card_range_half
        OnThisDayRange.YEAR -> R.string.soun_card_range_year
    }

/* 4 · Songs you drifted from ------------------------------------------------------------------ */

@Composable
fun DriftedHomeCard(
    drifted: DriftedSongs,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier) {
        CardHeader(stringResource(R.string.soun_card_drifted_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.width(132.dp).height(104.dp)) {
                val fan = drifted.songs.take(3)
                val angles = listOf(-9f, -2f, 7f)
                val offsets = listOf(0.dp to 10.dp, 24.dp to 4.dp, 48.dp to 12.dp)
                fan.forEachIndexed { i, song ->
                    Cover(
                        song.song.thumbnailUrl,
                        84.dp,
                        16.dp,
                        Modifier
                            .offset(offsets[i].first, offsets[i].second)
                            .rotate(angles[i])
                            .shadow(10.dp, RoundedCornerShape(16.dp)),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.soun_card_songs, drifted.songs.size, drifted.songs.size),
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    stringResource(
                        R.string.soun_card_drifted_text,
                        drifted.minPlays,
                        drifted.lastPlayed.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryPill(stringResource(R.string.soun_card_drifted_shuffle), R.drawable.shuffle, onShuffle, Modifier.weight(1f))
            RoundIconButton(R.drawable.queue_music, stringResource(R.string.add_to_queue), onAddToQueue)
        }
    }
}

/* 5 · Your sound at this hour ----------------------------------------------------------------- */

@Composable
fun TimeOfDayHomeCard(
    mix: TimeOfDayMix,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    HomeCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(ring, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    // The period's hours on a 24-hour dial (midnight at the top), the peak hour brighter.
                    val first = mix.period.hours.first()
                    drawArc(
                        SounAccent.copy(alpha = 0.4f),
                        first * 15f - 90f,
                        mix.period.hours.size * 15f,
                        false,
                        topLeft,
                        arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    drawArc(SounAccent, mix.peakHour * 15f - 90f, 15f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%02d:00".format(mix.peakHour), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
                    Text(stringResource(R.string.soun_card_hour_yours), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Eyebrow(stringResource(mix.period.labelRes()))
                Text(stringResource(mix.period.title()), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 23.sp)
                Text(
                    stringResource(R.string.soun_card_hour_text, "%02d:00".format(mix.peakHour)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mix.songs.take(4).forEach { song ->
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    Cover(song.song.thumbnailUrl, 200.dp, 12.dp, Modifier.fillMaxSize())
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryPill(stringResource(mix.period.playLabel()), R.drawable.play, onPlay, Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.soun_card_songs, mix.songs.size, mix.songs.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The period's name ("Evenings"), also the title of the queue it plays. */
fun DayPeriod.labelRes(): Int =
    when (this) {
        DayPeriod.MORNING -> R.string.soun_card_period_morning
        DayPeriod.AFTERNOON -> R.string.soun_card_period_afternoon
        DayPeriod.EVENING -> R.string.soun_card_period_evening
        DayPeriod.NIGHT -> R.string.soun_card_period_night
    }

private fun DayPeriod.title(): Int =
    when (this) {
        DayPeriod.MORNING -> R.string.soun_card_period_morning_title
        DayPeriod.AFTERNOON -> R.string.soun_card_period_afternoon_title
        DayPeriod.EVENING -> R.string.soun_card_period_evening_title
        DayPeriod.NIGHT -> R.string.soun_card_period_night_title
    }

private fun DayPeriod.playLabel(): Int =
    when (this) {
        DayPeriod.MORNING -> R.string.soun_card_period_morning_play
        DayPeriod.AFTERNOON -> R.string.soun_card_period_afternoon_play
        DayPeriod.EVENING -> R.string.soun_card_period_evening_play
        DayPeriod.NIGHT -> R.string.soun_card_period_night_play
    }

/* 6 · New from artists you follow ------------------------------------------------------------- */

@Composable
fun NewReleasesHomeCard(
    releases: List<SounReleaseWatcher.FoundRelease>,
    onOpen: (SounReleaseWatcher.FoundRelease) -> Unit,
    onPlay: (SounReleaseWatcher.FoundRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (releases.isEmpty()) return
    HomeCard(modifier) {
        CardHeader(stringResource(R.string.soun_card_releases_title))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            releases.take(2).forEach { release ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onOpen(release) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        Cover(release.thumbnailUrl, 400.dp, 18.dp, Modifier.fillMaxSize(), fallback = R.drawable.album)
                        Text(
                            stringResource(R.string.soun_card_releases_new),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = OnAccent,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(SounAccent)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        if (release.playlistId != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x8C141016))
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                        .clickable(onClickLabel = stringResource(R.string.play)) { onPlay(release) },
                            ) {
                                Icon(painterResource(R.drawable.play), contentDescription = stringResource(R.string.play), tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text(release.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${release.artist} · ${foundAgo(release.foundAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (releases.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun foundAgo(foundAt: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - foundAt).toInt()
    return when {
        days <= 0 -> stringResource(R.string.soun_card_releases_today)
        else -> pluralStringResource(R.plurals.soun_card_releases_days_ago, days, days)
    }
}

/* 7 · Your moments -------------------------------------------------------------------------- */

@Composable
fun MomentsHomeCard(
    moments: List<SavedMoment>,
    onPlay: (SavedMoment) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (moments.isEmpty()) return
    HomeCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenAll),
        ) {
            Text(
                stringResource(R.string.soun_card_moments_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                pluralStringResource(R.plurals.soun_card_moments_saved, moments.size, moments.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(painterResource(R.drawable.arrow_forward), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            moments.take(3).forEach { moment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clickable { onPlay(moment) }
                            .padding(8.dp),
                ) {
                    Cover(moment.thumbnailUrl, 44.dp, 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(moment.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(moment.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Chip(makeTimeString(moment.positionMs), accent = true)
                }
            }
        }
    }
}

/* Discover: songs picked by Gemini --------------------------------------------------------------- */

@Composable
fun DiscoverHomeCard(
    page: DiscoverPage?,
    hasKey: Boolean,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier, onClick = onOpen) {
        when {
            page == null -> {
                Eyebrow(stringResource(R.string.soun_discover_card_eyebrow))
                Text(
                    stringResource(if (hasKey && refreshing) R.string.soun_discover_card_picking else R.string.soun_discover_setup_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (!hasKey) {
                    Text(
                        stringResource(R.string.soun_discover_card_setup),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row { SecondaryPill(stringResource(R.string.soun_discover_setup_action), onOpen) }
                }
            }
            else -> {
                val period = runCatching { DayPeriod.valueOf(page.period) }.getOrNull()
                Eyebrow(
                    if (period != null) {
                        stringResource(R.string.soun_discover_card_for, stringResource(period.discoverLabel()))
                    } else {
                        stringResource(R.string.soun_discover)
                    },
                )
                SounGradientText(
                    text = page.intro.ifBlank { page.sections.first().title },
                    typingKey = "home_discover_${page.generatedAt}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.songs.take(5).forEach { song ->
                        Cover(song.thumbnailUrl, 52.dp, 12.dp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryPill(stringResource(R.string.play), R.drawable.play, onPlay, Modifier.weight(1f))
                    Text(
                        pluralStringResource(R.plurals.soun_discover_sections, page.sections.size, page.sections.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "evening", as in "for your evening". */
fun DayPeriod.discoverLabel(): Int =
    when (this) {
        DayPeriod.MORNING -> R.string.soun_discover_period_morning
        DayPeriod.AFTERNOON -> R.string.soun_discover_period_afternoon
        DayPeriod.EVENING -> R.string.soun_discover_period_evening
        DayPeriod.NIGHT -> R.string.soun_discover_period_night
    }
