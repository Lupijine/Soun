/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.LocalSounSurfaceBackdrop
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.utils.StatsBar
import com.metrolist.music.utils.StatsOverview
import com.metrolist.music.utils.StatsRange
import java.text.NumberFormat

/** "3 h 12 min", "45 min" or "<1 min". */
@Composable
fun formatListening(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    return when {
        ms in 1 until 60_000L -> stringResource(R.string.soun_stats_under_minute)
        minutes < 60 -> stringResource(R.string.soun_stats_minutes, minutes.toInt())
        else -> stringResource(R.string.soun_stats_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

/** Week / Month / Year / All time, with ‹ period › stepping below for the first three. */
@Composable
fun StatsRangeHeader(
    range: StatsRange,
    periodLabel: String,
    canGoBack: Boolean,
    onRangeChange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SounSegmentedControl(
            options = StatsRange.Kind.entries.toList(),
            selected = range.kind,
            label = { kind ->
                when (kind) {
                    StatsRange.Kind.WEEK -> stringResource(R.string.soun_stats_week)
                    StatsRange.Kind.MONTH -> stringResource(R.string.soun_stats_month)
                    StatsRange.Kind.YEAR -> stringResource(R.string.soun_stats_year)
                    StatsRange.Kind.ALL -> stringResource(R.string.soun_stats_all)
                }
            },
            onSelect = { onRangeChange(StatsRange(it, 0)) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        ) {
            val stepping = range.kind != StatsRange.Kind.ALL
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            StepButton(
                icon = if (rtl) R.drawable.navigate_next else R.drawable.arrow_back,
                description = stringResource(R.string.soun_stats_previous),
                visible = stepping && canGoBack,
                onClick = { onRangeChange(range.copy(offset = range.offset + 1)) },
            )
            Text(
                periodLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StepButton(
                icon = if (rtl) R.drawable.arrow_back else R.drawable.arrow_forward,
                description = stringResource(R.string.soun_stats_next),
                visible = stepping && range.offset > 0,
                onClick = { onRangeChange(range.copy(offset = (range.offset - 1).coerceAtLeast(0))) },
            )
        }
    }
}

@Composable
private fun StepButton(icon: Int, description: String, visible: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp).sounGlass(CircleShape, sounGlassColors(), backdrop = LocalSounSurfaceBackdrop.current),
            ) {
                Icon(painterResource(icon), description, Modifier.size(18.dp))
            }
        }
    }
}

/** Total time, the change against the previous period and the chart. */
@Composable
fun StatsHeroCard(
    overview: StatsOverview,
    rangeKind: StatsRange.Kind,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    var selectedBar by remember(overview.bars) { mutableStateOf<Int?>(null) }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .sounPanel(shape)
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.soun_stats_listening_time),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val minutes = overview.totalPlayTimeMs / 60_000L
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                NumberFormat.getIntegerInstance().format(minutes),
                fontSize = 48.sp,
                lineHeight = 50.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.soun_stats_minutes_unit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.weight(1f))
            overview.changePercent?.let { change -> ChangePill(change, Modifier.alignByBaseline()) }
        }
        if (minutes >= 60) {
            Text(
                formatListening(overview.totalPlayTimeMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 0.dp),
            )
        }

        if (overview.bars.isNotEmpty()) {
            val selected = selectedBar?.let { overview.bars.getOrNull(it) }
            Box(Modifier.fillMaxWidth().height(22.dp)) {
                if (selected != null) {
                    Text(
                        "${selected.label.ifBlank { "${(selectedBar ?: 0) + 1}" }} · ${formatListening(selected.playTimeMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SounAccent,
                    )
                }
            }
            StatsBarChart(
                bars = overview.bars,
                selected = selectedBar,
                onSelect = { selectedBar = if (selectedBar == it) null else it },
                height = 132.dp,
                barGap = if (rangeKind == StatsRange.Kind.MONTH) 3.dp else 8.dp,
            )
        }
    }
}

@Composable
private fun ChangePill(change: Int, modifier: Modifier = Modifier) {
    val up = change >= 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(if (up) SounAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(
            painterResource(if (up) R.drawable.arrow_upward else R.drawable.arrow_downward),
            contentDescription = null,
            tint = if (up) SounAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.soun_stats_change, kotlin.math.abs(change)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (up) SounAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Rounded bars on a faint track; tap a bar to read its value. */
@Composable
fun StatsBarChart(
    bars: List<StatsBar>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    height: Dp,
    barGap: Dp,
    modifier: Modifier = Modifier,
) {
    val max = (bars.maxOfOrNull { it.playTimeMs } ?: 0L).coerceAtLeast(1L)
    val grow by animateFloatAsState(1f, tween(600), label = "statsBarsGrow")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val slot = size.width / bars.size.toFloat()
                        val raw = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                        onSelect(if (rtl) bars.lastIndex - raw else raw)
                    }
                },
        ) {
            val gap = barGap.toPx()
            val slot = size.width / bars.size
            val width = (slot - gap).coerceAtLeast(2f)
            val radius = CornerRadius(width / 2f, width / 2f)
            bars.forEachIndexed { i, bar ->
                val position = if (rtl) bars.lastIndex - i else i
                val left = position * slot + (slot - width) / 2f
                drawRoundRect(track, Offset(left, 0f), Size(width, size.height), radius)
                if (bar.playTimeMs > 0) {
                    val h = (size.height * bar.playTimeMs / max * grow).coerceAtLeast(width)
                    val emphasised = bar.highlighted || i == selected
                    drawRoundRect(
                        SounAccent.copy(alpha = if (emphasised) 1f else 0.6f),
                        Offset(left, size.height - h),
                        Size(width, h),
                        radius,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEachIndexed { i, bar ->
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (bar.highlighted || i == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (bar.highlighted || i == selected) SounAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Plays · songs · artists. */
@Composable
fun StatsFactsRow(plays: Int, songs: Int, artists: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.padding(horizontal = 20.dp).fillMaxWidth(),
    ) {
        Fact(plays, stringResource(R.string.soun_stats_plays), Modifier.weight(1f))
        Fact(songs, stringResource(R.string.soun_stats_songs), Modifier.weight(1f))
        Fact(artists, stringResource(R.string.soun_stats_artists), Modifier.weight(1f))
    }
}

@Composable
private fun Fact(value: Int, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .sounPanel(RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(NumberFormat.getIntegerInstance().format(value), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** The #1 artist, large. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatsTopArtistCard(
    name: String,
    imageUrl: String?,
    playTimeMs: Long,
    plays: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .sounPanel(RoundedCornerShape(28.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.soun_stats_top_artist).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SounAccent,
            )
            Text(name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatListening(playTimeMs)} · ${androidx.compose.ui.res.pluralStringResource(R.plurals.n_time, plays, plays)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A ranked row for songs and artists inside a glass panel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatsRankedRow(
    rank: Int,
    imageUrl: String?,
    round: Boolean,
    title: String,
    subtitle: String?,
    detail: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            "$rank",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (rank <= 3) SounAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (isActive) SounAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/** A square album tile with its rank. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatsAlbumTile(
    rank: Int,
    imageUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .width(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Text(
                "$rank",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(top = 4.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Listening across the hours of the day, with the busiest hour called out. */
@Composable
fun StatsListeningClock(overview: StatsOverview, modifier: Modifier = Modifier) {
    val peak = overview.peakHour ?: return
    var selected by remember(overview.hourly) { mutableStateOf<Int?>(null) }
    val bars = overview.hourly.mapIndexed { hour, ms ->
        StatsBar(label = if (hour % 6 == 0) "%02d".format(hour) else "", playTimeMs = ms, highlighted = hour == peak)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .sounPanel(RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Text(
            stringResource(R.string.soun_stats_peak, "%02d:00".format(peak)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        val shown = selected
        Text(
            if (shown != null) "%02d:00 · %s".format(shown, formatListening(overview.hourly[shown]))
            else stringResource(R.string.soun_stats_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = if (shown != null) SounAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatsBarChart(
            bars = bars,
            selected = selected,
            onSelect = { selected = if (selected == it) null else it },
            height = 84.dp,
            barGap = 3.dp,
        )
    }
}
