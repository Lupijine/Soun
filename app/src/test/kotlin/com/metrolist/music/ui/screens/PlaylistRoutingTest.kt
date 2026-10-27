package com.metrolist.music.ui.screens

import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistRoutingTest {
    @Test fun `editable saved playlist resolves from both browse id forms`() = runBlocking {
        val saved = Playlist(PlaylistEntity(id = "local", name = "Mine", browseId = "PL123", isEditable = true), 480, emptyList())
        val find: suspend (String) -> Playlist? = { if (it == "PL123") saved else null }
        assertEquals("local", resolveEditablePlaylistId("PL123", find))
        assertEquals("local", resolveEditablePlaylistId("VLPL123", find))
    }

    @Test fun `public playlists remain online`() = runBlocking {
        val saved = Playlist(PlaylistEntity(id = "local", name = "Public", isEditable = false), 0, emptyList())
        assertNull(resolveEditablePlaylistId("PL123") { saved })
        assertNull(resolveEditablePlaylistId("PL999") { null })
    }
}
