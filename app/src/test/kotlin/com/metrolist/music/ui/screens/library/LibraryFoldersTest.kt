package com.metrolist.music.ui.screens.library

import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.utils.PlaylistFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFoldersTest {
    private val playlists = listOf("a", "b", "c", "d").map { Playlist(PlaylistEntity(id = it, name = it), 0, emptyList()) }

    @Test fun `filed playlists are hidden from the top level in their original order`() {
        val folders = listOf(PlaylistFolder("f1", "One", listOf("b")), PlaylistFolder("f2", "Two", listOf("d", "gone")))
        assertEquals(listOf("a", "c"), topLevelPlaylists(playlists, folders).map { it.id })
        assertEquals(playlists, topLevelPlaylists(playlists, emptyList()))
    }

    @Test fun `folder count ignores playlists that no longer exist`() {
        val folder = PlaylistFolder("f", "Mix", listOf("a", "gone", "c"))
        assertEquals(2, folder.liveCount(playlists.mapTo(HashSet()) { it.id }))
    }
}
