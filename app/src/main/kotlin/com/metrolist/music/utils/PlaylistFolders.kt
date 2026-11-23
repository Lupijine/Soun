/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.util.UUID

val PlaylistFoldersKey = stringPreferencesKey("soun_playlist_folders")

/** A library folder. [playlistIds] are PlaylistEntity ids (local and saved online playlists alike). */
@Immutable
@Serializable
data class PlaylistFolder(
    val id: String,
    val name: String,
    val playlistIds: List<String> = emptyList(),
)

/** Pure folder operations. A playlist lives in at most one folder; every call returns a new list. */
object PlaylistFolders {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PlaylistFolder.serializer())

    fun decode(raw: String?): List<PlaylistFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            Timber.w(e, "Ignoring unreadable playlist folders")
            emptyList()
        }
    }

    fun encode(folders: List<PlaylistFolder>): String = json.encodeToString(serializer, folders)

    fun add(
        folders: List<PlaylistFolder>,
        name: String,
        id: String = UUID.randomUUID().toString(),
        playlistIds: List<String> = emptyList(),
    ): List<PlaylistFolder> {
        val withoutMoved = folders.map { folder -> folder.copy(playlistIds = folder.playlistIds - playlistIds.toSet()) }
        return withoutMoved + PlaylistFolder(id = id, name = name.trim(), playlistIds = playlistIds.distinct())
    }

    fun rename(folders: List<PlaylistFolder>, folderId: String, name: String): List<PlaylistFolder> =
        folders.map { if (it.id == folderId) it.copy(name = name.trim()) else it }

    /** Removes only the folder; its playlists simply return to the top level. */
    fun delete(folders: List<PlaylistFolder>, folderId: String): List<PlaylistFolder> =
        folders.filterNot { it.id == folderId }

    /** Moves [playlistId] into [folderId], or back to the top level when [folderId] is null. */
    fun move(folders: List<PlaylistFolder>, playlistId: String, folderId: String?): List<PlaylistFolder> {
        if (folderId != null && folders.none { it.id == folderId }) return folders
        return folders.map { folder ->
            when {
                folder.id == folderId ->
                    if (playlistId in folder.playlistIds) folder else folder.copy(playlistIds = folder.playlistIds + playlistId)
                playlistId in folder.playlistIds -> folder.copy(playlistIds = folder.playlistIds - playlistId)
                else -> folder
            }
        }
    }

    fun folderOf(folders: List<PlaylistFolder>, playlistId: String): PlaylistFolder? =
        folders.firstOrNull { playlistId in it.playlistIds }

    /** Ids of every playlist that sits in some folder, so the top level can hide them. */
    fun filedPlaylistIds(folders: List<PlaylistFolder>): Set<String> =
        folders.flatMapTo(HashSet()) { it.playlistIds }
}

/** Folders stored as JSON in the app's preferences, so they need no database schema. */
class PlaylistFolderRepository(private val dataStore: DataStore<Preferences>) {
    val folders: Flow<List<PlaylistFolder>> =
        dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[PlaylistFoldersKey] }
            .distinctUntilChanged()
            .map { PlaylistFolders.decode(it) }

    suspend fun current(): List<PlaylistFolder> = folders.first()

    /** Returns the new folder's id. */
    suspend fun create(name: String, playlistIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { PlaylistFolders.add(it, name, id, playlistIds) }
        return id
    }

    suspend fun rename(folderId: String, name: String) = update { PlaylistFolders.rename(it, folderId, name) }

    suspend fun delete(folderId: String) = update { PlaylistFolders.delete(it, folderId) }

    suspend fun move(playlistId: String, folderId: String?) = update { PlaylistFolders.move(it, playlistId, folderId) }

    private suspend fun update(transform: (List<PlaylistFolder>) -> List<PlaylistFolder>): Boolean =
        try {
            dataStore.edit { prefs ->
                val next = transform(PlaylistFolders.decode(prefs[PlaylistFoldersKey]))
                if (next.isEmpty()) prefs.remove(PlaylistFoldersKey) else prefs[PlaylistFoldersKey] = PlaylistFolders.encode(next)
            }
            true
        } catch (e: IOException) {
            Timber.e(e, "Saving playlist folders failed")
            false
        }
}

fun Context.playlistFolderRepository() = PlaylistFolderRepository(applicationContext.dataStore)

@Composable
fun rememberPlaylistFolderRepository(): PlaylistFolderRepository {
    val context = LocalContext.current
    return remember(context) { context.playlistFolderRepository() }
}
