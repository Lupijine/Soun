package com.metrolist.innertube

import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer
import com.metrolist.innertube.models.MusicTwoRowItemRenderer
import com.metrolist.innertube.models.PlaylistItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ChartsItemTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `chart playlist remains navigable`() {
        val renderer = json.decodeFromString<MusicTwoRowItemRenderer>(
            """{
              "title":{"runs":[{"text":"Daily Top Music Videos"}]},
              "subtitle":{"runs":[{"text":"Chart"}]},
              "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/art.jpg"}]}}},
              "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLchart","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}
            }""",
        )

        val playlist = YouTube.chartItem(renderer) as PlaylistItem
        assertEquals("PLchart", playlist.id)
        assertEquals("Daily Top Music Videos", playlist.title)
    }

    @Test
    fun `chart artist remains navigable`() {
        val renderer = json.decodeFromString<MusicResponsiveListItemRenderer>(
            """{
              "flexColumns":[
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}},
                {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Subscribers"}]}}}
              ],
              "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.com/artist.jpg"}]}}},
              "navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}
            }""",
        )

        val artist = YouTube.chartItem(renderer) as ArtistItem
        assertEquals("UCartist", artist.id)
        assertEquals("Artist", artist.title)
    }
}
