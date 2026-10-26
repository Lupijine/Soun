/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.utils.isScrollingUp
import com.kyant.backdrop.Backdrop
import com.metrolist.music.ui.theme.LocalSounSurfaceBackdrop

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    backdrop: Backdrop? = LocalSounSurfaceBackdrop.current,
) {
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            if (onRecognitionClick != null) {
                SounActionButton(
                    icon = R.drawable.mic,
                    contentDescription = stringResource(R.string.recognize_music),
                    onClick = onRecognitionClick,
                    backdrop = backdrop,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            SounActionButton(
                icon = icon,
                contentDescription = contentDescription ?: stringResource(when (icon) {
                    R.drawable.mic -> R.string.recognize_music
                    R.drawable.upload -> R.string.upload_songs
                    else -> R.string.shuffle
                }),
                onClick = onClick,
                modifier = Modifier.size(60.dp),
                prominent = true,
                backdrop = backdrop,
            )
        }
    }
}
