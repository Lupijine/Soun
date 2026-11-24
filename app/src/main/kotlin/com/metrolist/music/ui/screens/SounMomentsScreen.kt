/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ui.component.sounCollectionRow
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.utils.SavedMoment
import com.metrolist.music.utils.SounMoments
import com.metrolist.music.utils.makeTimeString

/** Saved moments: tap one to play the song from that second. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SounMomentsScreen() {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val moments by SounMoments.moments(context).collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues()),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.soun_moments)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = { nav.navigateUp() }) {
                    Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
                }
            },
        )
        val list = moments.orEmpty()
        if (moments != null && list.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            ) {
                Icon(
                    painterResource(R.drawable.soun_moment),
                    contentDescription = null,
                    tint = SounAccent,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.soun_moments_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.id }) { moment ->
                MomentRow(
                    moment = moment,
                    onPlay = {
                        playerConnection?.playQueue(SounMoments.queueFrom(moment.songId, moment.positionMs))
                    },
                    onShare = { SounMoments.share(context, moment.title, moment.songId, moment.positionMs) },
                    onDelete = { SounMoments.remove(context, moment) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun MomentRow(
    moment: SavedMoment,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .sounCollectionRow(active = false, selected = false)
                .clickable(onClick = onPlay)
                .padding(10.dp),
    ) {
        Box {
            AsyncImage(
                model = moment.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(moment.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                moment.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = makeTimeString(moment.positionMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = SounAccent,
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SounAccent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        IconButton(onClick = onShare) {
            Icon(painterResource(R.drawable.share), stringResource(R.string.share), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.delete), stringResource(R.string.delete), modifier = Modifier.size(20.dp))
        }
    }
}
