/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/** Moves between the chapters of the current track; each returns false when there is none to go to. */
interface ChapterNavigator {
    fun nextChapter(): Boolean

    fun previousChapter(): Boolean
}

/**
 * The player as the media session sees it: the same player, except that
 * - while a lyric line is set it is shown in place of the artist, and
 * - in a track with chapters, the current chapter is shown as the title (the track's own title
 *   moves to the artist line) and next/previous move between chapters,
 * so the lock screen, the notification, headphones and car displays follow along. The app
 * itself keeps using the real player.
 *
 * [line], [chapter] and [chapterNavigator] must be set on the player's application (main) thread.
 */
@UnstableApi
class LyricLinePlayer(player: Player) : ForwardingPlayer(player) {
    private val sessionListeners = CopyOnWriteArraySet<Player.Listener>()

    var line: String? = null
        set(value) {
            val clean = value?.trim()?.takeIf { it.isNotEmpty() }
            if (field == clean) return
            field = clean
            notifyMetadataChanged()
        }

    var chapter: String? = null
        set(value) {
            val clean = value?.trim()?.takeIf { it.isNotEmpty() }
            if (field == clean) return
            field = clean
            notifyMetadataChanged()
        }

    var chapterNavigator: ChapterNavigator? = null

    /** True while the current track has chapters; next/previous then stay available. */
    var hasChapters: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyAvailableCommandsChanged()
        }

    override fun addListener(listener: Player.Listener) {
        sessionListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        sessionListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val currentChapter = chapter
        val currentLine = line
        if (currentChapter == null && currentLine == null) return base
        val builder = base.buildUpon()
        if (currentChapter != null) {
            builder.setTitle(currentChapter).setDisplayTitle(currentChapter)
            val trackTitle = base.title ?: base.displayTitle
            builder.setArtist(currentLine ?: trackTitle).setSubtitle(currentLine ?: trackTitle)
        } else {
            builder.setArtist(currentLine).setSubtitle(currentLine)
        }
        return builder.build()
    }

    override fun seekToNext() {
        if (chapterNavigator?.nextChapter() != true) super.seekToNext()
    }

    override fun seekToPrevious() {
        if (chapterNavigator?.previousChapter() != true) super.seekToPrevious()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        super.isCommandAvailable(command) || (hasChapters && command in CHAPTER_COMMANDS)

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        if (!hasChapters) return base
        return base.buildUpon().addAll(*CHAPTER_COMMANDS).build()
    }

    private fun notifyMetadataChanged() {
        val metadata = mediaMetadata
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        sessionListeners.forEach { listener ->
            listener.onMediaMetadataChanged(metadata)
            listener.onEvents(this, events)
        }
    }

    private fun notifyAvailableCommandsChanged() {
        val commands = availableCommands
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED).build())
        sessionListeners.forEach { listener ->
            listener.onAvailableCommandsChanged(commands)
            listener.onEvents(this, events)
        }
    }

    private companion object {
        val CHAPTER_COMMANDS = intArrayOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
    }
}
