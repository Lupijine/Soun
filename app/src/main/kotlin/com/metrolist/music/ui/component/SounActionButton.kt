package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.metrolist.music.ui.theme.LocalSounSurfaceBackdrop
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors

@Composable
fun SounActionButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    prominent: Boolean = false,
    enabled: Boolean = true,
    backdrop: Backdrop? = LocalSounSurfaceBackdrop.current,
) {
    val shape = RoundedCornerShape(22.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = 52.dp)
            .widthIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .sounGlass(shape, sounGlassColors(), elevation = 6f, backdrop = backdrop, fullPanel = true)
            .then(if (prominent) Modifier.background(
                Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.24f), colors.primary.copy(alpha = 0.06f))),
                shape,
            ) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (label == null) 12.dp else 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), if (label == null) contentDescription else null,
            Modifier.size(22.dp), tint = colors.onSurface)
        if (label != null) Text(label, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
