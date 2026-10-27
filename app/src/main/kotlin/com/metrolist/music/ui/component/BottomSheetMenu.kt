/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.Backdrop
import com.metrolist.music.ui.theme.LocalSounBackdrop
import com.metrolist.music.ui.theme.sounGlass
import com.metrolist.music.ui.theme.sounSurface
import com.metrolist.music.ui.theme.sounGlassColors
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState as rememberMaterialBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var backdrop by mutableStateOf<Backdrop?>(null)
        private set
    var content by mutableStateOf(content)
    var lightweight by mutableStateOf(false)
        private set

    fun show(lightweight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
        show(null, lightweight, content)
    }

    fun show(backdrop: Backdrop?, lightweight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
        this.backdrop = backdrop
        this.lightweight = lightweight
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberMaterialBottomSheetState(initialValue = SheetValue.Hidden),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), // Soun: rounder sheets
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f), // Soun: glass sheet
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    var lastContent by remember { mutableStateOf(content) }

    LaunchedEffect(content) {
        if (isVisible) {
            lastContent = content
        }
    }

    LaunchedEffect(isVisible) {
        if (!isVisible) sheetState.hide()
    }

    if (!sheetState.isVisible && !isVisible) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = {
            if (isVisible) content() else lastContent()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), // Soun: glass sheet
) {
    val focusManager = LocalFocusManager.current
    val sheetState = rememberMaterialBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val maximumHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f

    AnimatedBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = maximumHeight).then(if (state.lightweight) Modifier.sounSurface(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) else Modifier.sounGlass(
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                sounGlassColors().copy(surfaceTint = background.copy(alpha = 0.76f)),
                backdrop = state.backdrop ?: LocalSounBackdrop.current,
                // Keep the moving sheet to one blur/lens pass instead of the full-panel shader chain.
                fullPanel = false,
                diffusion = 10.dp,
                elevation = 0f,
            )),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(vertical = 12.dp).size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 20.dp)) {
                state.content(this)
            }
        }
    }
}
