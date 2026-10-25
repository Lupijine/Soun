/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.theme.*
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

private enum class ThemeSource { SOUN, ARTWORK, WALLPAPER, CUSTOM }

private val accentColors = listOf(
    R.string.palette_soun to SounAccent,
    R.string.palette_rose to Color(0xFFD81B60),
    R.string.palette_purple to Color(0xFF8E24AA),
    R.string.palette_blue to Color(0xFF1E88E5),
    R.string.palette_cyan to Color(0xFF00ACC1),
    R.string.palette_teal to Color(0xFF00897B),
    R.string.palette_green to Color(0xFF43A047),
    R.string.palette_amber to Color(0xFFFFB300),
    R.string.palette_orange to Color(0xFFFB8C00),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(navController: NavController) {
    var darkMode by rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    var pureBlack by rememberPreference(PureBlackKey, false)
    var legacyMiniBlack by rememberPreference(PureBlackMiniPlayerKey, false)
    var artworkAccent by rememberPreference(DynamicThemeKey, true)
    var seed by rememberPreference(SelectedThemeColorKey, SounAccent.toArgb())
    var sourceDialog by rememberSaveable { mutableStateOf(false) }
    val player = LocalPlayerConnection.current
    val metadata by player?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val artworkColors = rememberArtworkColors(metadata?.thumbnailUrl,
        enabled = !pureBlack || MaterialTheme.colorScheme.surface.luminance() >= 0.5f)
    val source = when {
        artworkAccent -> ThemeSource.ARTWORK
        seed == SounAccent.toArgb() -> ThemeSource.SOUN
        seed == DefaultThemeColor.toArgb() && Build.VERSION.SDK_INT >= 31 -> ThemeSource.WALLPAPER
        else -> ThemeSource.CUSTOM
    }
    val sourceName: @Composable (ThemeSource) -> String = {
        stringResource(when (it) {
            ThemeSource.SOUN -> R.string.palette_soun
            ThemeSource.ARTWORK -> R.string.soun_theme_artwork
            ThemeSource.WALLPAPER -> R.string.soun_theme_wallpaper
            ThemeSource.CUSTOM -> R.string.soun_theme_custom
        })
    }
    if (sourceDialog) {
        EnumDialog(
            title = stringResource(R.string.soun_theme_source),
            current = source,
            values = listOf(ThemeSource.SOUN, ThemeSource.ARTWORK) +
                (if (Build.VERSION.SDK_INT >= 31) listOf(ThemeSource.WALLPAPER) else emptyList()) + ThemeSource.CUSTOM,
            valueText = sourceName,
            onSelect = {
                artworkAccent = it == ThemeSource.ARTWORK
                when (it) {
                    ThemeSource.SOUN -> seed = SounAccent.toArgb()
                    ThemeSource.WALLPAPER -> seed = DefaultThemeColor.toArgb()
                    ThemeSource.CUSTOM -> if (seed == DefaultThemeColor.toArgb() || seed == SounAccent.toArgb()) {
                        seed = accentColors[1].second.toArgb()
                    }
                    ThemeSource.ARTWORK -> Unit
                }
                sourceDialog = false
            },
            onDismiss = { sourceDialog = false },
        )
    }
    LazyColumn(
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "preview") { SounThemePreview(pureBlack, artworkColors) }
        item(key = "mode") {
            Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mode, label) in listOf(DarkMode.AUTO to R.string.soun_theme_system,
                    DarkMode.OFF to R.string.soun_theme_light, DarkMode.ON to R.string.soun_theme_dark)) {
                    FilterChip(selected = darkMode == mode, onClick = { darkMode = mode },
                        label = { Text(stringResource(label)) }, modifier = Modifier.weight(1f))
                }
            }
            Material3SettingsGroup(items = listOf(Material3SettingsItem(
                title = { Text(stringResource(R.string.pure_black)) },
                description = { Text(stringResource(R.string.soun_theme_black_desc)) },
                trailingContent = { Switch(checked = pureBlack, onCheckedChange = { pureBlack = it; legacyMiniBlack = it }) },
                onClick = { pureBlack = !pureBlack; legacyMiniBlack = pureBlack },
            )))
        }
        item(key = "source") {
            Material3SettingsGroup(items = listOf(Material3SettingsItem(
                icon = painterResource(R.drawable.palette),
                title = { Text(stringResource(R.string.soun_theme_source)) },
                description = { Text(sourceName(source)) },
                onClick = { sourceDialog = true },
            )))
            Text(stringResource(R.string.soun_theme_source_desc), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        item(key = "accents") {
            Text(stringResource(R.string.soun_theme_custom), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                for (row in accentColors.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((name, color) in row) {
                            val selected = !artworkAccent && seed == color.toArgb()
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                        seed = color.toArgb(); artworkAccent = false
                                    }).padding(vertical = 12.dp)) {
                                Box(Modifier.size(40.dp).background(color, CircleShape)
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier))
                                Text(stringResource(name), style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    TopAppBar(
        modifier = Modifier.sounPanel(androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        title = { Text(stringResource(R.string.theme)) }, navigationIcon = {
        IconButton(onClick = { navController.navigateUp() }) {
            Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.cd_back))
        }
    })
}

@Composable
private fun SounThemePreview(pureBlack: Boolean, artwork: List<Color>) {
    val backdrop = rememberLayerBackdrop()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp))) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop).sounAmbientBackground(pureBlack && dark, artwork))
        CompositionLocalProvider(LocalSounSurfaceBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("Soun", style = MaterialTheme.typography.headlineLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)))
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(20.dp)))
                }
                Row(Modifier.fillMaxWidth().sounPanel(RoundedCornerShape(24.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.soun_theme_preview), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge)
                    Icon(painterResource(R.drawable.play), contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
