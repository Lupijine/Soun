/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.NavigationBarHeight
import com.metrolist.music.constants.SlimNavBarHeight
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounGlassColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.metrolist.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Soun's floating Liquid Glass navigation: a glass pill holding the main tabs, with
 * Search split out into its own round glass button on the right (iOS 26 style).
 * Keeps the same signature and height as the Material bar it replaces, so the
 * surrounding layout in MainActivity is unchanged.
 */
@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val glass = sounGlassColors(strong = true, pureBlack = pureBlack)
    val barHeight = (if (slimNav) SlimNavBarHeight else NavigationBarHeight) - 16.dp
    val pillShape = RoundedCornerShape(percent = 50)

    val searchItem = navigationItems.firstOrNull { it == Screens.Search }
    val tabItems = navigationItems.filter { it != Screens.Search }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .height(barHeight),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .sounGlass(pillShape, glass)
                        .selectableGroup()
                        .padding(if (slimNav) 0.dp else 5.dp),
            ) {
                val selectedIndex = tabItems.indexOfFirst { isRouteSelected(currentRoute, it.route, navigationItems) }
                val indicatorPosition by animateFloatAsState(
                    targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
                    label = "glassTabPosition",
                )
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (selectedIndex >= 0) 1f else 0f,
                    label = "glassTabVisibility",
                )
                if (tabItems.isNotEmpty()) {
                    val tabWidth = maxWidth / tabItems.size
                    Box(
                        Modifier.offset(x = tabWidth * indicatorPosition)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp, vertical = if (slimNav) 3.dp else 0.dp)
                            .graphicsLayer { alpha = indicatorAlpha }
                            .background(glass.selectedPill, pillShape)
                            .border(0.5.dp, glass.rimBright.copy(alpha = 0.35f), pillShape),
                    )
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    tabItems.forEach { screen ->
                        SounNavItem(
                            screen = screen,
                            isSelected = isRouteSelected(currentRoute, screen.route, navigationItems),
                            showLabel = !slimNav,
                            onItemClick = onItemClick,
                            onSearchLongClick = null,
                            onHomeLongHold = onHomeLongHold,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }

            if (searchItem != null) {
                val isSelected = isRouteSelected(currentRoute, searchItem.route, navigationItems)
                Box(
                    modifier =
                        Modifier
                            .size(barHeight)
                            .sounGlass(CircleShape, glass)
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    SounNavItem(
                        screen = searchItem,
                        isSelected = isSelected,
                        showLabel = false,
                        onItemClick = onItemClick,
                        onSearchLongClick = onSearchLongClick,
                        onHomeLongHold = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SounNavItem(
    screen: Screens,
    isSelected: Boolean,
    showLabel: Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchLongClick: (() -> Unit)?,
    onHomeLongHold: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val currentIsSelected by rememberUpdatedState(isSelected)
    val iconRes = if (isSelected) screen.iconIdActive else screen.iconIdInactive

    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
    val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "glassTabPress",
    )
    var longPressTriggered by remember { mutableStateOf(false) }
    val currentSearchLongClick by rememberUpdatedState(onSearchLongClick)
    val currentHomeLongHold by rememberUpdatedState(onHomeLongHold)

    // Long press detection using InteractionSource
    if (isSearchItem || isHomeHoldItem) {
        LaunchedEffect(interactionSource, isSearchItem, isHomeHoldItem, viewConfiguration.longPressTimeoutMillis) {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        longPressTriggered = false
                        delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                        longPressTriggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isHomeHoldItem) currentHomeLongHold?.invoke() else currentSearchLongClick?.invoke()
                    }
                    is PressInteraction.Cancel -> {
                        longPressTriggered = false
                    }
                }
            }
        }
    }

    val contentColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.78f),
        label = "glassTabColor",
    )
    val title = stringResource(screen.titleId)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .selectable(
                    selected = isSelected,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = {
                        if (!longPressTriggered) {
                            onItemClick(screen, currentIsSelected)
                        }
                        longPressTriggered = false
                    },
                ),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = if (showLabel) null else title,
            tint = contentColor,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        )
        if (showLabel) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
