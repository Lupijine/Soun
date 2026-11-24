/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.C
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.metrolist.music.extensions.metadata
import kotlin.time.Duration.Companion.minutes

/** Soun: where a sleep timer can stop besides a set time. */
enum class SleepStop { SONG, CHAPTER, ALBUM }

class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
    private val onVolumeMultiplierChanged: (Float) -> Unit = {},
) : Player.Listener {
    private companion object {
        private const val TIMER_TICK_MS = 1000L
        private const val FADE_OUT_WINDOW_MS = 60_000L
    }

    private var sleepTimerJob: Job? = null
    var triggerTime by mutableLongStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    var stopAfterCurrentSongOnTimeout by mutableStateOf(false)
        private set
    var fadeOutEnabled by mutableStateOf(false)
        private set

    /** Soun: stopping at the end of the current part (chapter) or album; null otherwise. */
    var stopAt by mutableStateOf<SleepStop?>(null)
        private set
    private var stopAtPositionMs = -1L
    private var stopAtMediaId: String? = null
    private var stopAtAlbumId: String? = null

    /** How long the timer had when it started, for the countdown ring. */
    var armedTotalMs by mutableLongStateOf(0L)
        private set

    val isActive: Boolean
        get() = triggerTime != -1L || pauseWhenSongEnd || stopAt != null

    fun start(minute: Int) {
        start(
            minute = minute,
            stopAfterCurrentSong = false,
            fadeOut = false,
        )
    }

    fun start(
        minute: Int,
        stopAfterCurrentSong: Boolean,
        fadeOut: Boolean,
    ) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        updateVolumeMultiplier(1f)
        fadeOutEnabled = fadeOut
        stopAt = null
        armedTotalMs =
            if (minute == -1) {
                songRemainingMs()
            } else {
                minute.minutes.inWholeMilliseconds
            }

        if (minute == -1) {
            pauseWhenSongEnd = true
            stopAfterCurrentSongOnTimeout = false
            triggerTime = -1L
            if (fadeOutEnabled) {
                sleepTimerJob =
                    scope.launch {
                        while (this@SleepTimer.isActive) {
                            updateVolumeMultiplierForCurrentSong()
                            delay(TIMER_TICK_MS)
                        }
                    }
            }
        } else {
            pauseWhenSongEnd = false
            stopAfterCurrentSongOnTimeout = stopAfterCurrentSong
            triggerTime = System.currentTimeMillis() + minute.minutes.inWholeMilliseconds
            sleepTimerJob =
                scope.launch {
                    while (this@SleepTimer.isActive) {
                        if (triggerTime != -1L) {
                            val remainingMs = triggerTime - System.currentTimeMillis()
                            if (remainingMs <= 0L) {
                                triggerTime = -1L
                                if (stopAfterCurrentSongOnTimeout) {
                                    pauseWhenSongEnd = true
                                    stopAfterCurrentSongOnTimeout = false
                                    if (!fadeOutEnabled) {
                                        break
                                    }
                                } else {
                                    completeTimerAndPause()
                                    break
                                }
                            } else if (fadeOutEnabled && !stopAfterCurrentSongOnTimeout) {
                                updateVolumeMultiplierForRemainingTime(remainingMs)
                            }
                        } else if (pauseWhenSongEnd && fadeOutEnabled) {
                            updateVolumeMultiplierForCurrentSong()
                        }

                        delay(TIMER_TICK_MS)
                    }
                }
        }
    }

    /**
     * Soun: pause at the end of the current part of a long track ([chapterEndMs], a position in
     * the current track) or at the end of the current album (when the next song is from
     * another album, or the queue runs out).
     */
    fun startAtEndOf(
        target: SleepStop,
        fadeOut: Boolean,
        chapterEndMs: Long? = null,
    ) {
        val albumId = player.currentMediaItem?.metadata?.album?.id
        if (target == SleepStop.SONG ||
            (target == SleepStop.CHAPTER && chapterEndMs == null) ||
            (target == SleepStop.ALBUM && albumId == null)
        ) {
            start(minute = -1, stopAfterCurrentSong = false, fadeOut = fadeOut)
            return
        }
        sleepTimerJob?.cancel()
        updateVolumeMultiplier(1f)
        fadeOutEnabled = fadeOut
        pauseWhenSongEnd = false
        stopAfterCurrentSongOnTimeout = false
        triggerTime = -1L
        stopAt = target
        stopAtMediaId = player.currentMediaItem?.mediaId
        stopAtPositionMs = chapterEndMs ?: -1L
        stopAtAlbumId = albumId
        armedTotalMs = remainingMs() ?: 0L
        sleepTimerJob =
            scope.launch {
                while (stopAt != null) {
                    when (stopAt) {
                        SleepStop.CHAPTER -> {
                            if (player.currentMediaItem?.mediaId != stopAtMediaId) {
                                completeTimerAndPause()
                                break
                            }
                            val left = stopAtPositionMs - player.currentPosition
                            if (left <= 250L) {
                                completeTimerAndPause()
                                break
                            }
                            if (fadeOutEnabled) updateVolumeMultiplierForRemainingTime(left)
                        }
                        SleepStop.ALBUM -> {
                            if (fadeOutEnabled) {
                                if (nextIsFromAnotherAlbum()) updateVolumeMultiplierForCurrentSong() else updateVolumeMultiplier(1f)
                            }
                        }
                        else -> Unit
                    }
                    delay(if (stopAt == SleepStop.CHAPTER) 250L else TIMER_TICK_MS)
                }
            }
    }

    /** Adds [minutes] to a running countdown. */
    fun extend(minutes: Int) {
        if (triggerTime == -1L) return
        triggerTime += minutes.minutes.inWholeMilliseconds
        armedTotalMs += minutes.minutes.inWholeMilliseconds
    }

    /** Time until the timer pauses, or null when it can't be known. */
    fun remainingMs(): Long? =
        when {
            triggerTime != -1L -> (triggerTime - System.currentTimeMillis()).coerceAtLeast(0L)
            pauseWhenSongEnd -> songRemainingMs()
            stopAt == SleepStop.CHAPTER -> (stopAtPositionMs - player.currentPosition).coerceAtLeast(0L)
            stopAt == SleepStop.ALBUM -> albumRemainingMs()
            else -> null
        }

    private fun songRemainingMs(): Long {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return 0L
        return (duration - player.currentPosition).coerceAtLeast(0L)
    }

    /** The current song's rest plus the following songs of the same album in the queue. */
    private fun albumRemainingMs(): Long {
        var total = songRemainingMs()
        var index = player.nextMediaItemIndex
        val albumId = stopAtAlbumId ?: return total
        val seen = HashSet<Int>()
        while (index != C.INDEX_UNSET && seen.add(index) && index < player.mediaItemCount) {
            val metadata = player.getMediaItemAt(index).metadata ?: break
            if (metadata.album?.id != albumId) break
            total += metadata.duration * 1000L
            index = player.currentTimeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return total
    }

    private fun nextIsFromAnotherAlbum(): Boolean {
        val next = player.nextMediaItemIndex
        if (next == C.INDEX_UNSET) return true
        return player.getMediaItemAt(next).metadata?.album?.id != stopAtAlbumId
    }

    /**
     * Notify the sleep timer that a song transition has occurred outside of normal
     * player callbacks (e.g. during crossfade player swap). If "end of song" mode
     * is active, this will pause the player and deactivate the timer.
     */
    fun notifySongTransition() {
        if (pauseWhenSongEnd) {
            completeTimerAndPause()
        } else {
            checkStopAtTransition(player.currentMediaItem)
        }
    }

    private fun checkStopAtTransition(mediaItem: MediaItem?) {
        when (stopAt) {
            SleepStop.CHAPTER -> if (mediaItem?.mediaId != stopAtMediaId) completeTimerAndPause()
            SleepStop.ALBUM -> if (mediaItem?.metadata?.album?.id != stopAtAlbumId) completeTimerAndPause()
            else -> Unit
        }
    }

    fun clear() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        stopAt = null
        pauseWhenSongEnd = false
        stopAfterCurrentSongOnTimeout = false
        fadeOutEnabled = false
        triggerTime = -1L
        updateVolumeMultiplier(1f)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (pauseWhenSongEnd) {
            completeTimerAndPause()
        } else {
            checkStopAtTransition(mediaItem)
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == Player.STATE_ENDED && (pauseWhenSongEnd || stopAt != null)) {
            completeTimerAndPause()
        }
    }

    private fun completeTimerAndPause() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        stopAt = null
        pauseWhenSongEnd = false
        stopAfterCurrentSongOnTimeout = false
        fadeOutEnabled = false
        triggerTime = -1L
        updateVolumeMultiplier(1f)
        player.pause()
    }

    private fun updateVolumeMultiplierForRemainingTime(remainingMs: Long) {
        updateVolumeMultiplier(volumeMultiplierForRemainingTime(remainingMs))
    }

    private fun updateVolumeMultiplierForCurrentSong() {
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) {
            updateVolumeMultiplier(1f)
            return
        }

        val remainingMs = (duration - player.currentPosition).coerceAtLeast(0L)
        updateVolumeMultiplierForRemainingTime(remainingMs)
    }

    private fun volumeMultiplierForRemainingTime(remainingMs: Long): Float {
        if (remainingMs >= FADE_OUT_WINDOW_MS) return 1f
        return (remainingMs.toFloat() / FADE_OUT_WINDOW_MS).coerceIn(0f, 1f)
    }

    private fun updateVolumeMultiplier(multiplier: Float) {
        onVolumeMultiplierChanged(multiplier)
    }
}
