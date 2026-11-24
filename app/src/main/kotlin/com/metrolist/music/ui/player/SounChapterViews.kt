/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.playback.Chapter
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.delay

/** The title of a chapter, or "Part N" when the description gave it none. */
@Composable
fun chapterTitle(chapters: List<Chapter>, index: Int): String =
    chapters.getOrNull(index)?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.soun_chapter_untitled, index + 1)

/**
 * A small pill under the song's artists in a long track with chapters: which part is playing,
 * "3/12 · Song". Tapping it lists all the parts.
 */
@Composable
fun SounChapterPill(
    chapters: List<Chapter>,
    currentIndex: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chapters.isEmpty()) return
    val index = currentIndex.coerceIn(0, chapters.lastIndex)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(
            painterResource(R.drawable.list),
            contentDescription = stringResource(R.string.soun_chapters),
            tint = color.copy(alpha = 0.85f),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "${index + 1}/${chapters.size}",
            color = color.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        )
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "chapterTitle",
        ) { shown ->
            Text(
                text = chapterTitle(chapters, shown),
                color = color.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Resumed at 42:10 · Start over": shown for a few seconds when a long track picks up where it
 * was left.
 */
@Composable
fun SounResumePill(
    positionMs: Long,
    color: Color,
    onStartOver: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(positionMs) {
        delay(8_000)
        onTimeout()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.12f))
                .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Icon(
            painterResource(R.drawable.history),
            contentDescription = null,
            tint = color.copy(alpha = 0.85f),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = stringResource(R.string.soun_resumed_at, makeTimeString(positionMs)),
            color = color.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.soun_start_over),
            color = SounAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onStartOver)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** The list of a track's chapters, shown in the glass sheet; tapping one plays from there. */
@Composable
fun ColumnScope.SounChaptersMenu(
    trackTitle: String,
    chapters: List<Chapter>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (currentIndex > 2) listState.scrollToItem((currentIndex - 2).coerceAtLeast(0))
    }
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.soun_chapters),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = stringResource(R.string.soun_chapters_count, chapters.size) + " · " + trackTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
    ) {
        itemsIndexed(chapters) { index, chapter ->
            val current = index == currentIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (current) SounAccent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    text = makeTimeString(chapter.startMs),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (current) SounAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    text = chapterTitle(chapters, index),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (current) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SounAccent),
                    )
                }
            }
        }
    }
}
