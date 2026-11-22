/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors

/**
 * Compact song header shown at the top of Now Playing while the lyrics are open, so the
 * lyrics get the middle of the screen instead of sharing it with a large title block.
 */
@Composable
internal fun SounLyricsHeader(
    mediaMetadata: MediaMetadata,
    isFavorite: Boolean,
    showArtwork: Boolean,
    cropArtwork: Boolean,
    textColor: Color,
    backdrop: Backdrop?,
    onTitleClick: () -> Unit,
    onToggleLike: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tapping the song (cover or title) closes the lyrics and returns to the artwork view.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.close),
                    onClick = onTitleClick,
                ),
        ) {
            val artworkModifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            if (showArtwork && mediaMetadata.thumbnailUrl != null) {
                AsyncImage(
                    model = mediaMetadata.thumbnailUrl,
                    contentDescription = null,
                    contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
                    modifier = artworkModifier,
                )
            } else {
                Box(artworkModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaMetadata.title,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artists = mediaMetadata.artists.map { it.name }.filter { it.isNotBlank() }
                if (artists.isNotEmpty()) {
                    Text(
                        text = artists.joinToString(),
                        color = textColor.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleLike,
            modifier = Modifier.size(44.dp).sounGlass(CircleShape, sounGlassColors(), backdrop = backdrop),
        ) {
            Icon(
                painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(R.string.like),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else textColor,
                modifier = Modifier.size(22.dp),
            )
        }

        IconButton(
            onClick = onMore,
            modifier = Modifier.size(44.dp).sounGlass(CircleShape, sounGlassColors(), backdrop = backdrop),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more_options),
                tint = textColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
