package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFoldersTest {
    private val base = listOf(
        PlaylistFolder("a", "Chill", listOf("p1", "p2")),
        PlaylistFolder("b", "Gym", listOf("p3")),
    )

    @Test fun `add appends an empty folder with a trimmed name`() {
        val result = PlaylistFolders.add(base, "  Road trip ", id = "c")
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(PlaylistFolder("c", "Road trip", emptyList()), result.last())
        assertEquals(base, result.dropLast(1))
    }

    @Test fun `add with playlists takes them out of their old folder`() {
        val result = PlaylistFolders.add(base, "New", id = "c", playlistIds = listOf("p2", "p2", "p9"))
        assertEquals(listOf("p1"), result.first { it.id == "a" }.playlistIds)
        assertEquals(listOf("p2", "p9"), result.first { it.id == "c" }.playlistIds)
    }

    @Test fun `rename changes only that folder`() {
        val result = PlaylistFolders.rename(base, "b", " Workout ")
        assertEquals(listOf("Chill", "Workout"), result.map { it.name })
        assertEquals(base[1].playlistIds, result[1].playlistIds)
        assertEquals(base, PlaylistFolders.rename(base, "missing", "x"))
    }

    @Test fun `delete drops the folder and frees its playlists`() {
        val result = PlaylistFolders.delete(base, "a")
        assertEquals(listOf("b"), result.map { it.id })
        assertEquals(setOf("p3"), PlaylistFolders.filedPlaylistIds(result))
        assertEquals(base, PlaylistFolders.delete(base, "missing"))
    }

    @Test fun `move files a playlist in exactly one folder`() {
        val result = PlaylistFolders.move(base, "p1", "b")
        assertEquals(listOf("p2"), result.first { it.id == "a" }.playlistIds)
        assertEquals(listOf("p3", "p1"), result.first { it.id == "b" }.playlistIds)
        assertEquals("b", PlaylistFolders.folderOf(result, "p1")?.id)
    }

    @Test fun `move of an unfiled playlist adds it once`() {
        val once = PlaylistFolders.move(base, "p7", "a")
        val twice = PlaylistFolders.move(once, "p7", "a")
        assertEquals(listOf("p1", "p2", "p7"), twice.first { it.id == "a" }.playlistIds)
    }

    @Test fun `move to null returns the playlist to the top level`() {
        val result = PlaylistFolders.move(base, "p3", null)
        assertTrue(result.first { it.id == "b" }.playlistIds.isEmpty())
        assertNull(PlaylistFolders.folderOf(result, "p3"))
    }

    @Test fun `move to an unknown folder changes nothing`() {
        assertEquals(base, PlaylistFolders.move(base, "p1", "missing"))
    }

    @Test fun `json round trips and tolerates bad input`() {
        assertEquals(base, PlaylistFolders.decode(PlaylistFolders.encode(base)))
        assertEquals(emptyList<PlaylistFolder>(), PlaylistFolders.decode(null))
        assertEquals(emptyList<PlaylistFolder>(), PlaylistFolders.decode(""))
        assertEquals(emptyList<PlaylistFolder>(), PlaylistFolders.decode("{not json"))
        assertEquals(
            listOf(PlaylistFolder("x", "Old", emptyList())),
            PlaylistFolders.decode("""[{"id":"x","name":"Old","extra":1}]"""),
        )
    }
}
