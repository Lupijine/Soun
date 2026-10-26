package com.metrolist.music.ui.component

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.DownloadOverview
import com.metrolist.music.playback.FailedDownload
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DownloadStatusStripTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null

    @After fun closeHost() { compose.runOnUiThread { host?.pause()?.stop()?.destroy() } }

    @Test
    fun `failed downloads stay compact until expanded and retry still targets the right song`() {
        val retries = mutableListOf<String?>()
        val cancellations = mutableListOf<String>()
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.testTag("strip")) {
                            DownloadStatusStrip(
                                DownloadPanelState(DownloadOverview(29, 0, 0, listOf(FailedDownload("song-id", "Space Song")), null), false, 447_000_000),
                                onStorageClick = {}, onPauseChange = {}, onRetry = { retries += it }, onCancel = { cancellations += it },
                            )
                        }
                    }
                }
            }
        }
        assertTrue(compose.onNodeWithTag("strip").getUnclippedBoundsInRoot().height <= 72.dp)
        compose.onNodeWithText("Space Song").assertDoesNotExist()
        compose.onNodeWithText("Retry 1").performClick()
        compose.runOnIdle { assertEquals(listOf<String?>(null), retries) }
        compose.onNodeWithContentDescription("Show download details").performClick()
        compose.onNodeWithText("Space Song").assertIsDisplayed()
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()
        compose.runOnIdle { assertEquals(listOf(null, "song-id"), retries) }
        compose.onNodeWithContentDescription("Cancel download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("song-id"), cancellations) }
    }
    @Test
    fun `active and failed downloads fit a narrow screen and pause and resume remain usable`() {
        val state = mutableStateOf(DownloadPanelState(DownloadOverview(29, 210, 0,
            List(7) { FailedDownload("id$it", "Song $it") }, 0.4f), false, 447_000_000))
        val commands = mutableListOf<Boolean>()
        val cancelled = mutableListOf<String>()
        var storageClicks = 0
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.width(320.dp).testTag("strip")) {
                                DownloadStatusStrip(state.value, { storageClicks++ },
                                    { commands += it; state.value = state.value.copy(paused = it) }, {}, { id ->
                                        cancelled += id
                                        state.value = state.value.copy(overview = state.value.overview.copy(
                                            failed = state.value.overview.failed.filterNot { it.id == id },
                                        ))
                                    })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Retry 7").assertIsDisplayed()
        compose.onNodeWithContentDescription("Manage").performClick()
        compose.onNodeWithContentDescription("Pause downloads").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Resume downloads").assertIsDisplayed().performClick()
        assertTrue(compose.onNodeWithTag("strip").getUnclippedBoundsInRoot().height <= 80.dp)
        compose.runOnIdle { assertEquals(listOf(true, false), commands); assertEquals(1, storageClicks) }
        compose.onNodeWithContentDescription("Show download details").performClick()
        compose.onAllNodesWithContentDescription("Cancel download").onFirst().assertIsDisplayed().performClick()
        compose.onNodeWithText("Song 0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("id0"), cancelled) }
    }

}
