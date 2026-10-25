/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import com.metrolist.music.ui.theme.sounPanel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.constants.HideStatusBarOnFullscreenKey
import com.metrolist.music.constants.LyricsAnimationStyle
import com.metrolist.music.constants.LyricsAnimationStyleKey
import com.metrolist.music.constants.LyricsClickKey
import com.metrolist.music.constants.LyricsGlowEffectKey
import com.metrolist.music.constants.LyricsLineSpacingKey
import com.metrolist.music.constants.LyricsScrollKey
import com.metrolist.music.constants.LyricsTextPositionKey
import com.metrolist.music.constants.LyricsTextSizeKey
import com.metrolist.music.constants.RespectAgentPositioningKey
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.EnumDialog
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSettings(navController: NavController) {
    val (lyricsPosition, onLyricsPositionChange) =
        rememberEnumPreference(
            LyricsTextPositionKey,
            defaultValue = LyricsPosition.CENTER,
        )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) =
        rememberPreference(
            LyricsScrollKey,
            defaultValue = true,
        )
    val (hideStatusBarOnFullscreen, onHideStatusBarOnFullscreenChange) =
        rememberPreference(
            HideStatusBarOnFullscreenKey,
            defaultValue = false,
        )
    val (respectAgentPositioning, onRespectAgentPositioningChange) = rememberPreference(RespectAgentPositioningKey, defaultValue = true)
    val (experimentalLyrics, onExperimentalLyricsChange) = rememberPreference(ExperimentalLyricsKey, defaultValue = true)

    val (lyricsGlowEffect, onLyricsGlowEffectChange) = rememberPreference(LyricsGlowEffectKey, defaultValue = false)
    val (lyricsAnimationStyle, onLyricsAnimationStyleChange) =
        rememberEnumPreference(
            LyricsAnimationStyleKey,
            defaultValue = LyricsAnimationStyle.APPLE,
        )
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 24f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)

    var showLyricsStyleDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsAnimationStyleDialog by remember { mutableStateOf(false) }
    var showLyricsTextSizeDialog by remember { mutableStateOf(false) }
    var showLyricsLineSpacingDialog by remember { mutableStateOf(false) }

    var showLyricsPositionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showLyricsPositionDialog) {
        EnumDialog(
            onDismiss = { showLyricsPositionDialog = false },
            onSelect = {
                onLyricsPositionChange(it)
                showLyricsPositionDialog = false
            },
            title = stringResource(R.string.lyrics_text_position),
            current = lyricsPosition,
            values = LyricsPosition.values().toList(),
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            },
        )
    }

    if (showLyricsAnimationStyleDialog) {
        EnumDialog(
            onDismiss = { showLyricsAnimationStyleDialog = false },
            onSelect = {
                onLyricsAnimationStyleChange(it)
                showLyricsAnimationStyleDialog = false
            },
            title = stringResource(R.string.lyrics_animation_style_title),
            current = lyricsAnimationStyle,
            values = LyricsAnimationStyle.values().toList(),
            valueText = {
                when (it) {
                    LyricsAnimationStyle.NONE -> stringResource(R.string.lyrics_animation_none)
                    LyricsAnimationStyle.FADE -> stringResource(R.string.lyrics_animation_fade)
                    LyricsAnimationStyle.GLOW -> stringResource(R.string.lyrics_animation_glow)
                    LyricsAnimationStyle.SLIDE -> stringResource(R.string.lyrics_animation_slide)
                    LyricsAnimationStyle.KARAOKE -> stringResource(R.string.lyrics_animation_karaoke)
                    LyricsAnimationStyle.APPLE -> stringResource(R.string.lyrics_animation_apple)
                }
            },
        )
    }

    if (showLyricsTextSizeDialog) {
        var tempTextSize by remember { mutableFloatStateOf(lyricsTextSize) }

        DefaultDialog(
            onDismiss = {
                tempTextSize = lyricsTextSize
                showLyricsTextSizeDialog = false
            },
            buttons = {
                TextButton(
                    onClick = {
                        tempTextSize = 24f
                    },
                ) {
                    Text(stringResource(R.string.reset))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        tempTextSize = lyricsTextSize
                        showLyricsTextSizeDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onLyricsTextSizeChange(tempTextSize)
                        showLyricsTextSizeDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.lyrics_text_size),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text(
                    text = "${tempTextSize.roundToInt()} sp",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Slider(
                    value = tempTextSize,
                    onValueChange = { tempTextSize = it },
                    valueRange = 12f..48f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showLyricsLineSpacingDialog) {
        var tempLineSpacing by remember { mutableFloatStateOf(lyricsLineSpacing) }

        DefaultDialog(
            onDismiss = {
                tempLineSpacing = lyricsLineSpacing
                showLyricsLineSpacingDialog = false
            },
            buttons = {
                TextButton(
                    onClick = {
                        tempLineSpacing = 1.3f
                    },
                ) {
                    Text(stringResource(R.string.reset))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        tempLineSpacing = lyricsLineSpacing
                        showLyricsLineSpacingDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        onLyricsLineSpacingChange(tempLineSpacing)
                        showLyricsLineSpacingDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.lyrics_line_spacing),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text(
                    text = String.format(Locale.US, "%.1f", tempLineSpacing),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Slider(
                    value = tempLineSpacing,
                    onValueChange = { tempLineSpacing = it },
                    valueRange = 1.0f..3.0f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showLyricsStyleDialog) {
        EnumDialog(
            title = stringResource(R.string.soun_lyrics_style),
            current = experimentalLyrics,
            values = listOf(true, false),
            valueText = { stringResource(if (it) R.string.soun_expressive_lyrics else R.string.soun_classic_lyrics) },
            valueDescription = { stringResource(if (it) R.string.soun_expressive_lyrics_desc else R.string.soun_classic_lyrics_desc) },
            onSelect = { onExperimentalLyricsChange(it); showLyricsStyleDialog = false },
            onDismiss = { showLyricsStyleDialog = false },
        )
    }

    Column(
        Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Material3SettingsGroup(
            title = stringResource(R.string.lyrics),
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.lyrics),
                            title = { Text(stringResource(R.string.soun_lyrics_style)) },
                            description = { Text(stringResource(if (experimentalLyrics) R.string.soun_expressive_lyrics else R.string.soun_classic_lyrics)) },
                            onClick = { showLyricsStyleDialog = true },
                        ),
                    )

                    if (!experimentalLyrics) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.lyrics),
                                title = { Text(stringResource(R.string.lyrics_glow_effect)) },
                                description = { Text(stringResource(R.string.lyrics_glow_effect_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = lyricsGlowEffect,
                                        onCheckedChange = onLyricsGlowEffectChange,
                                        thumbContent = {
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        id = if (lyricsGlowEffect) R.drawable.check else R.drawable.close,
                                                    ),
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                            )
                                        },
                                    )
                                },
                                onClick = { onLyricsGlowEffectChange(!lyricsGlowEffect) },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.lyrics),
                                title = { Text(stringResource(R.string.lyrics_animation_style_title)) },
                                description = {
                                    Text(
                                        when (lyricsAnimationStyle) {
                                            LyricsAnimationStyle.NONE -> stringResource(R.string.lyrics_animation_none)
                                            LyricsAnimationStyle.FADE -> stringResource(R.string.lyrics_animation_fade)
                                            LyricsAnimationStyle.GLOW -> stringResource(R.string.lyrics_animation_glow)
                                            LyricsAnimationStyle.SLIDE -> stringResource(R.string.lyrics_animation_slide)
                                            LyricsAnimationStyle.KARAOKE -> stringResource(R.string.lyrics_animation_karaoke)
                                            LyricsAnimationStyle.APPLE -> stringResource(R.string.lyrics_animation_apple)
                                        },
                                    )
                                },
                                onClick = { showLyricsAnimationStyleDialog = true },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.lyrics),
                                title = { Text(stringResource(R.string.lyrics_text_size)) },
                                description = { Text("${lyricsTextSize.roundToInt()} sp") },
                                onClick = { showLyricsTextSizeDialog = true },
                            ),
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.lyrics),
                                title = { Text(stringResource(R.string.lyrics_line_spacing)) },
                                description = { Text(String.format(Locale.US, "%.1f", lyricsLineSpacing)) },
                                onClick = { showLyricsLineSpacingDialog = true },
                            ),
                        )
                    }

                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.lyrics),
                            title = { Text(stringResource(R.string.lyrics_text_position)) },
                            description = {
                                Text(
                                    when (lyricsPosition) {
                                        LyricsPosition.LEFT -> stringResource(R.string.left)
                                        LyricsPosition.CENTER -> stringResource(R.string.center)
                                        LyricsPosition.RIGHT -> stringResource(R.string.right)
                                    },
                                )
                            },
                            onClick = { showLyricsPositionDialog = true },
                        ),
                    )
                    if (experimentalLyrics) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.lyrics),
                                title = { Text(stringResource(R.string.respect_agent_positioning)) },
                                description = { Text(stringResource(R.string.respect_agent_positioning_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = respectAgentPositioning,
                                        onCheckedChange = onRespectAgentPositioningChange,
                                        thumbContent = {
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        id = if (respectAgentPositioning) R.drawable.check else R.drawable.close,
                                                    ),
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                            )
                                        },
                                    )
                                },
                                onClick = { onRespectAgentPositioningChange(!respectAgentPositioning) },
                            ),
                        )
                    }
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.lyrics),
                            title = { Text(stringResource(R.string.lyrics_click_change)) },
                            trailingContent = {
                                Switch(
                                    checked = lyricsClick,
                                    onCheckedChange = onLyricsClickChange,
                                    thumbContent = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (lyricsClick) R.drawable.check else R.drawable.close,
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                            },
                            onClick = { onLyricsClickChange(!lyricsClick) },
                        ),
                    )
                    if (!experimentalLyrics) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.lyrics),
                                title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
                                trailingContent = {
                                    Switch(
                                        checked = lyricsScroll,
                                        onCheckedChange = onLyricsScrollChange,
                                        thumbContent = {
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        id = if (lyricsScroll) R.drawable.check else R.drawable.close,
                                                    ),
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                            )
                                        },
                                    )
                                },
                                onClick = { onLyricsScrollChange(!lyricsScroll) },
                            ),
                        )
                    }
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.lyrics),
                            title = { Text(stringResource(R.string.hide_status_bar_fullscreen)) },
                            description = { Text(stringResource(R.string.hide_status_bar_fullscreen_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = hideStatusBarOnFullscreen,
                                    onCheckedChange = onHideStatusBarOnFullscreenChange,
                                    thumbContent = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    id = if (hideStatusBarOnFullscreen) R.drawable.check else R.drawable.close,
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                            },
                            onClick = { onHideStatusBarOnFullscreenChange(!hideStatusBarOnFullscreen) },
                        ),
                    )
                },
        )

        Spacer(modifier = Modifier.height(27.dp))

    }
    TopAppBar(
        modifier = Modifier.sounPanel(androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),

        title = { Text(stringResource(R.string.lyrics)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.cd_back))
            }
        },
    )
}
