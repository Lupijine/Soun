/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.WEEK_DAYS
import com.metrolist.music.utils.WeekSummary
import com.metrolist.music.utils.weekStart
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

/** Glass summary of the last 7 days of listening, shown at the top of Home. */
@Composable
fun YourWeekCard(
    summary: WeekSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val minutes = remember(summary.totalMinutes) { NumberFormat.getIntegerInstance().format(summary.totalMinutes) }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .sounPanel(shape)
            .clickable(onClickLabel = stringResource(R.string.soun_week_open_stats), onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.soun_week_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row {
                    Text(
                        minutes,
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.2).sp,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.soun_week_minutes_unit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(
                    stringResource(R.string.soun_week_range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WeekBars(summary, Modifier.width(140.dp))
        }

        val artist = summary.topArtist
        val song = summary.topSong
        if (artist != null || song != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (artist != null) {
                    WeekHighlight(
                        label = stringResource(R.string.soun_week_top_artist),
                        title = artist.title,
                        imageUrl = artist.thumbnailUrl,
                        imageShape = CircleShape,
                        fallbackIcon = R.drawable.artist,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (song != null) {
                    WeekHighlight(
                        label = stringResource(R.string.soun_week_top_song),
                        title = song.title,
                        imageUrl = song.thumbnailUrl,
                        imageShape = RoundedCornerShape(10.dp),
                        fallbackIcon = R.drawable.music_note,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Seven lavender bars, oldest day first; today is drawn at full strength. */
@Composable
private fun WeekBars(summary: WeekSummary, modifier: Modifier = Modifier) {
    val daily = summary.dailyPlayTimeMs
    val todayIndex = daily.lastIndex
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val todayLabel = sounLavenderInk()
    val description = stringResource(R.string.soun_week_chart_description)
    val labels = remember(summary.today) {
        val first = weekStart(summary.today)
        List(WEEK_DAYS) { first.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            if (daily.isEmpty()) return@Canvas
            val slot = size.width / daily.size
            val barWidth = slot * 0.52f
            val radius = CornerRadius(barWidth / 2f)
            val peak = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
            val minBar = 4.dp.toPx()
            val rtl = layoutDirection == LayoutDirection.Rtl
            daily.forEachIndexed { index, playTime ->
                // Mirror with the day labels below, which the Row lays out right-to-left in RTL.
                val position = if (rtl) daily.lastIndex - index else index
                val left = slot * position + (slot - barWidth) / 2f
                drawRoundRect(track, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = radius)
                if (playTime > 0L) {
                    val height = max(minBar, size.height * (playTime / peak))
                    drawRoundRect(
                        color = SounAccent.copy(alpha = if (index == todayIndex) 1f else 0.45f),
                        topLeft = Offset(left, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == todayIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == todayIndex) todayLabel else labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekHighlight(
    label: String,
    title: String,
    imageUrl: String?,
    imageShape: Shape,
    @DrawableRes fallbackIcon: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(imageShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Icon(
                painterResource(fallbackIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl.resize(120, 120),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
