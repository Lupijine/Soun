package com.metrolist.music.ui.component

import android.app.Application
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.metrolist.music.ui.theme.LocalAmbientDrift
import com.metrolist.music.ui.theme.sounAmbientBackground
import com.metrolist.music.ui.theme.rememberAmbientDrift
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import com.metrolist.music.ui.theme.SounCollectionTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import com.metrolist.music.R
import com.metrolist.music.playback.DownloadOverview
import com.metrolist.music.playback.FailedDownload
import com.metrolist.music.ui.theme.SounCollectionBackground
import com.metrolist.music.ui.theme.SounTypography
import java.io.File
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.GraphicsMode
import com.metrolist.music.ui.theme.LocalSounArtworkAccent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionPresentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    @After fun closeHost() { compose.runOnUiThread { host?.pause()?.stop()?.destroy() } }

    private fun content(block: @Composable () -> Unit) {
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            host!!.get().setContent {
                MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFCEB8E9), surface = Color(0xFF111017)), typography = SounTypography) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { block() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun saveImage(tag: String, name: String) {
        System.getenv("SOUN_VISUAL_DIR")?.let { directory ->
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            File(directory).mkdirs()
            File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun `downloaded collection shows songs in the first viewport and balanced playback controls work`() {
        reviewCollection("Downloaded", R.drawable.soun_downloads_chrome, true)
    }

    @Test fun `liked collection uses the same full page presentation without a cover card`() {
        reviewCollection("Liked", R.drawable.soun_liked_chrome, false)
    }

    private fun reviewCollection(name: String, artwork: Int, downloaded: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { context.imageLoader.execute(ImageRequest.Builder(context).data(artwork).size(640).build()) }
        var plays = 0
        var shuffles = 0
        content {
            Box(Modifier.fillMaxSize().testTag("collection")) {
                SounCollectionBackground(if (downloaded) listOf(Color(0xFF786EAA), Color(0xFF5F9AB2)) else listOf(Color(0xFF9A5D8A), Color(0xFF657DAF)))
                Column {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                        Text(name, Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
                        Icon(painterResource(R.drawable.search), null)
                    }
                    LazyColumn {
                        item {
                            SounCollectionHeader(name, if (downloaded) "29 songs · 4:59:15" else "488 songs · 2:08:44:15", artwork,
                                null, true, { plays++ }, { shuffles++ }, {})
                        }
                        if (downloaded) item { DownloadStatusStrip(DownloadPanelState(DownloadOverview(29, 0, 0,
                            listOf(FailedDownload("song-id", "Space Song")), null), false, 447_000_000), {}, {}, {}, {}) }
                        item { Text("Date added  ↓", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.labelLarge) }
                        items(6) { index ->
                            ListItem(title = listOf("Space Song", "Серые глаза", "Moon, Moon", "The Line", "Beautiful Life", "Blue Sands")[index],
                                subtitle = { Text(listOf("Beach House · 5:21", "Ирина Салтыкова · 3:26", "Sofia Rotaru · 4:36")[index % 3], style = MaterialTheme.typography.bodySmall) },
                                thumbnailContent = { Box(Modifier.size(48.dp).background(
                                    listOf(Color(0xFF782F46), Color(0xFF6579A1), Color(0xFF648575))[index % 3], RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.music_note), null, Modifier.size(20.dp))
                                } }, isActive = index == 0,
                                trailingContent = { Icon(painterResource(R.drawable.more_vert), null) })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Play", ignoreCase = true).performClick()
        compose.onNodeWithText("Shuffle", ignoreCase = true).performClick()
        compose.runOnIdle { assertEquals(1, plays); assertEquals(1, shuffles) }
        compose.onNodeWithText("Space Song").assertIsDisplayed()
        val playWidth = compose.onNodeWithText("Play", ignoreCase = true).getUnclippedBoundsInRoot().width
        val shuffleWidth = compose.onNodeWithText("Shuffle", ignoreCase = true).getUnclippedBoundsInRoot().width
        assertEquals(playWidth.value, shuffleWidth.value, 1f)
        saveImage("collection", if (downloaded) "downloaded-page.png" else "liked-page.png")
    }

    @Test fun `empty editable playlist keeps its menu available while playback is disabled`() {
        var menus = 0
        content {
            SounCollectionHeader("New playlist", "0 songs", null, null, false, {}, {}, { menus++ }, moreEnabled = true)
        }
        compose.onNodeWithText("Play", ignoreCase = true).assertIsNotEnabled()
        compose.onNodeWithText("Shuffle", ignoreCase = true).assertIsNotEnabled()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.runOnIdle { assertEquals(1, menus) }
    }

    @Test fun `collection accent stays independent when the playing song changes the app theme`() {
        val playingColor = mutableStateOf(Color.Red)
        var accent = Color.Unspecified
        content {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = playingColor.value)) {
                SounCollectionTheme(Color(0xFF568DCA)) {
                    val current = MaterialTheme.colorScheme.primary
                    SideEffect { accent = current }
                    Text("Collection")
                }
            }
        }
        val original = compose.runOnIdle { accent }
        compose.runOnIdle { playingColor.value = Color.Green }
        compose.runOnIdle { assertEquals(original, accent) }
    }

    @Test fun `manual accent overrides collection artwork and updates without reopening the page`() {
        val chosen = mutableStateOf(Color.Red)
        var accent = Color.Unspecified
        content {
            CompositionLocalProvider(LocalSounArtworkAccent provides false) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = chosen.value, tertiary = chosen.value)) {
                    SounCollectionTheme(Color.Blue) {
                        val current = MaterialTheme.colorScheme.primary
                        SideEffect { accent = current }
                        SounCollectionBackground(listOf(Color.Blue), modifier = Modifier.testTag("manual-page"))
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(Color.Red, accent) }
        val red = compose.onNodeWithTag("manual-page").captureToImage().asAndroidBitmap()
        val x = red.width / 2
        val y = red.height / 5
        assertTrue(android.graphics.Color.red(red.getPixel(x, y)) > android.graphics.Color.blue(red.getPixel(x, y)))
        compose.runOnIdle { chosen.value = Color.Green }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(Color.Green, accent) }
        val green = compose.onNodeWithTag("manual-page").captureToImage().asAndroidBitmap()
        assertTrue(android.graphics.Color.green(green.getPixel(x, y)) > android.graphics.Color.red(green.getPixel(x, y)))
    }

    @Test fun `remote collection header retains creator and save actions alongside playback`() {
        var saves = 0
        var plays = 0
        var shuffles = 0
        var menus = 0
        content {
            SounCollectionHeader(
                name = "THE INSPECTION", subtitle = "19 songs · 51:06", artwork = null,
                colorSource = null, thumbnail = "file:///missing-cover.jpg", enabled = true,
                onPlay = { plays++ }, onShuffle = { shuffles++ }, onMore = { menus++ },
                supportingContent = {
                    Text("COCHISE")
                    TextButton(onClick = { saves++ }) { Text("Save playlist") }
                },
            )
        }
        compose.onNodeWithText("COCHISE").assertIsDisplayed()
        compose.onNodeWithText("Save playlist").performClick()
        compose.onNodeWithText("Play", ignoreCase = true).performClick()
        compose.onNodeWithText("Shuffle", ignoreCase = true).performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 1, 1), listOf(saves, plays, shuffles, menus)) }
    }

    @Test fun `ambient light visibly moves without recomposing or redrawing the cached foreground`() {
        val phase = mutableStateOf(0f)
        var compositions = 0
        var foregroundDraws = 0
        content {
            CompositionLocalProvider(LocalAmbientDrift provides phase) {
                assertTrue(rememberAmbientDrift() === phase)
                SideEffect { compositions++ }
                Box(Modifier.fillMaxSize().testTag("moving-page")) {
                    Box(Modifier.fillMaxSize().layerBackdrop(rememberLayerBackdrop()).sounAmbientBackground(artwork = listOf(Color.Red, Color.Blue)))
                    Box(Modifier.size(120.dp).graphicsLayer().drawBehind { foregroundDraws++; drawRect(Color.White) })
                }
            }
        }
        val before = compose.onNodeWithTag("moving-page").captureToImage().asAndroidBitmap()
        val oldCompositions = compositions
        val oldDraws = foregroundDraws
        compose.runOnIdle { phase.value = 0.25f }
        val after = compose.onNodeWithTag("moving-page").captureToImage().asAndroidBitmap()
        var changed = 0
        for (y in 150 until before.height step 20) for (x in 0 until before.width step 20) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
        }
        assertTrue("The visible light field must move", changed > 50)
        assertEquals("Motion must stay in the draw phase", oldCompositions, compositions)
        assertEquals("Static content must reuse its layer", oldDraws, foregroundDraws)
    }

    @Test fun `pure black collection has no coloured light or translucent overlay`() {
        content { SounCollectionBackground(listOf(Color.White, Color.Magenta), pureBlack = true, modifier = Modifier.testTag("black-page")) }
        val bitmap = compose.onNodeWithTag("black-page").captureToImage().asAndroidBitmap()
        for ((x, y) in listOf(bitmap.width / 2 to bitmap.height / 5, bitmap.width / 3 to bitmap.height / 2)) {
            assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(x, y))
        }
    }

    @Test fun `short menu wraps its content instead of reserving most of the screen`() {
        content {
            Box(Modifier.fillMaxSize()) {
                SounCollectionBackground(listOf(Color(0xFF786EAA)))
                BottomSheetMenu(modifier = Modifier.testTag("menu-surface"), state = androidx.compose.runtime.remember {
                    MenuState(true) {
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Text("Add to queue", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(20.dp))
                            Text("Export playlist", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(20.dp))
                            Text("Remove download", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                })
            }
        }
        compose.onNodeWithText("Add to queue").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("menu-surface").getUnclippedBoundsInRoot().height <= 260.dp)

    }
}
