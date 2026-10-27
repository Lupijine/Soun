package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors

val LocalSounNotice = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun SounSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.widthIn(max = 480.dp).padding(horizontal = 24.dp)) { data ->
        val colors = MaterialTheme.colorScheme
        Snackbar(
            modifier = Modifier.sounGlass(
                RoundedCornerShape(24.dp),
                sounGlassColors().copy(surfaceTint = colors.surfaceContainer.copy(alpha = 0.72f)),
                elevation = 8f, fullPanel = true, diffusion = 8.dp,
            ),
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            dismissAction = if (data.visuals.withDismissAction) {
                { androidx.compose.material3.IconButton(onClick = data::dismiss) {
                    Icon(painterResource(R.drawable.close), stringResource(R.string.close))
                } }
            } else null,
            action = data.visuals.actionLabel?.let { label ->
                { TextButton(onClick = data::performAction) { Text(label) } }
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.info), null, Modifier.size(20.dp), tint = colors.primary)
                Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
