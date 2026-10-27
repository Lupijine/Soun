package com.metrolist.music.ui.screens.library

import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedLibraryTest {
    @Test fun `pinned playlists come first without disturbing their original order`() {
        val playlists = listOf("a", "b", "c", "d").map { Playlist(PlaylistEntity(id = it, name = it), 0, emptyList()) }
        assertEquals(listOf("a", "c", "b", "d"), orderPinnedPlaylists(playlists, setOf("c", "a", "missing")).map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), orderPinnedPlaylists(playlists, emptySet()).map { it.id })
    }
}
