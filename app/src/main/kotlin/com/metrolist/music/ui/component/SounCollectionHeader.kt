package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R

@Composable
internal fun SounCollectionHeader(
    name: String,
    subtitle: String,
    @DrawableRes artwork: Int?,
    colorSource: String?,
    enabled: Boolean,
    onPlay: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: String? = null,
    moreEnabled: Boolean = enabled,
    showActions: Boolean = true,
    supportingContent: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (artwork != null || thumbnail == null) {
            SounPlaylistArtwork(artwork, Modifier.padding(top = 12.dp, bottom = 16.dp).size(248.dp), colorSource = colorSource)
        } else {
            AsyncImage(thumbnail, null, contentScale = ContentScale.Crop,
                modifier = Modifier.padding(top = 24.dp, bottom = 28.dp).size(216.dp).clip(RoundedCornerShape(28.dp)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onMore, enabled = moreEnabled) {
                Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.more_options))
            }
        }
        Text(subtitle, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        supportingContent()
        if (showActions) SounCollectionActions(enabled, onPlay, onShuffle)
    }
}

@Composable
internal fun SounCollectionActions(enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SounActionButton(R.drawable.soun_collection_play, stringResource(R.string.play), onPlay,
            Modifier.weight(1f), label = stringResource(R.string.play), enabled = enabled, prominent = true)
        SounActionButton(R.drawable.soun_collection_shuffle, stringResource(R.string.shuffle), onShuffle,
            Modifier.weight(1f), label = stringResource(R.string.shuffle), enabled = enabled)
    }
}
