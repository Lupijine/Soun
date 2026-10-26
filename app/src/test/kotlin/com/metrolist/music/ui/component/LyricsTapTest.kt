package com.metrolist.music.ui.component

import android.app.Application
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import org.junit.After
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LyricsTapTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var host: ActivityController<ComponentActivity>? = null

    private fun setContent(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent(content = content)
        }
        compose.waitForIdle()
    }

    @After
    fun closeHost() {
        compose.runOnUiThread { host?.pause()?.stop()?.destroy() }
    }

    @Test
    fun `playback recomposition while finger is down preserves tap and uses current callback`() {
        val playbackFrame = mutableIntStateOf(0)
        val clicks = mutableListOf<Int>()
        setContent {
            val frame = playbackFrame.intValue
            Text("Line $frame", Modifier.testTag("line").lyricsTap({ clicks += frame }, {}).height(100.dp).fillMaxWidth())
        }
        compose.onNodeWithTag("line").performTouchInput { down(center) }
        repeat(5) { compose.runOnIdle { playbackFrame.intValue++ } }
        compose.onNodeWithTag("line").performTouchInput { advanceEventTime(80); up() }
        compose.runOnIdle { assertEquals(listOf(5), clicks) }
    }

    @Test
    fun `one tap while lyrics scroll seeks once and manual drag does not seek`() {
        var clicks = 0
        lateinit var scroll: () -> Unit
        setContent {
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            scroll = { scope.launch { state.animateScrollBy(200f, androidx.compose.animation.core.tween(1500)) } }
            LazyColumn(Modifier.height(400.dp), state = state) {
                items(20) { index ->
                    Box(Modifier.testTag("line$index").fillMaxWidth().height(80.dp).lyricsTap({ clicks++ }, {}))
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scroll() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("line2").performTouchInput { down(center); advanceEventTime(40); up() }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithTag("line2").performTouchInput {
            down(center)
            moveBy(Offset(0f, -100f), delayMillis = 32)
            up()
        }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun `accessible click invokes the current line callback`() {
        var clicks = 0
        setContent {
            Text("Line", Modifier.testTag("line").lyricsTap({ clicks++ }, {}))
        }
        compose.onNodeWithTag("line").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun `small finger movement stays a tap inside the lyrics drag surface`() {
        var clicks = 0
        var scrollDelta = 0f
        setContent {
            Box(Modifier.draggable(rememberDraggableState { scrollDelta += it }, Orientation.Vertical)) {
                Text("Line", Modifier.testTag("line").fillMaxWidth().height(100.dp).lyricsTap({ clicks++ }, {}))
            }
        }
        compose.onNodeWithTag("line").performTouchInput {
            down(center)
            moveBy(Offset(0f, 1f), delayMillis = 40)
            up()
        }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0f, scrollDelta)
        }
    }

    @Test
    fun `holding a line selects once without seeking`() {
        var clicks = 0
        var holds = 0
        setContent {
            Text("Line", Modifier.testTag("line").fillMaxWidth().height(100.dp).lyricsTap({ clicks++ }, { holds++ }))
        }
        compose.onNodeWithTag("line").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(0, clicks)
            assertEquals(1, holds)
        }
    }
}
