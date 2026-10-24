/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.lyrics.LyricsLoadStatus
import com.metrolist.music.lyrics.usableLyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LyricsViewModel

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val (experimentalLyrics, _) = rememberPreference(key = ExperimentalLyricsKey, defaultValue = true)

    val connection = LocalPlayerConnection.current ?: return
    val metadata by connection.mediaMetadata.collectAsStateWithLifecycle()
    val saved by connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    var retry by remember(metadata?.id) { mutableIntStateOf(0) }
    var status by remember(metadata?.id) { mutableStateOf(LyricsLoadStatus.LOADING) }
    LaunchedEffect(metadata?.id, showLyrics, retry) {
        val song = metadata ?: return@LaunchedEffect
        if (showLyrics) {
            status = LyricsLoadStatus.LOADING
            status = try {
                connection.service.lyricsRepository.load(song)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LyricsLoadStatus.ERROR
            }
        }
    }

    if (showLyrics && !usableLyrics(saved?.takeIf { it.id == metadata?.id }?.lyrics) && status != LyricsLoadStatus.FOUND) {
        Box(modifier) { LyricsLoadPanel(status) { retry += 1 } }
        return
    }

    if (experimentalLyrics) {
        ExperimentalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = modifier,
            showLyrics = showLyrics,
            lyricsViewModel = lyricsViewModel
        )
    } else {
        OriginalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = modifier,
            showLyrics = showLyrics
        )
    }
}
