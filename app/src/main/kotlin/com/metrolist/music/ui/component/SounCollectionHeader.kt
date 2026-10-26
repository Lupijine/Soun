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
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: String? = null,
    moreEnabled: Boolean = enabled,
) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (artwork != null || thumbnail == null) {
            SounPlaylistArtwork(artwork, Modifier.padding(vertical = 4.dp).size(176.dp), colorSource = colorSource)
        } else {
            AsyncImage(thumbnail, null, contentScale = ContentScale.Crop,
                modifier = Modifier.padding(vertical = 8.dp).size(176.dp).clip(RoundedCornerShape(18.dp)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onMore, enabled = moreEnabled) {
                Icon(painterResource(R.drawable.more_horiz), stringResource(R.string.more_options))
            }
        }
        Text(subtitle, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SounActionButton(R.drawable.play, stringResource(R.string.play), onPlay,
                Modifier.weight(1f), label = stringResource(R.string.play), enabled = enabled, prominent = true)
            SounActionButton(R.drawable.shuffle, stringResource(R.string.shuffle), onShuffle,
                Modifier.weight(1f), label = stringResource(R.string.shuffle), enabled = enabled)
        }
    }
}
