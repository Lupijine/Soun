/**
 * Soun (based on Metrolist) (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.format.DateFormat
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.DiscoverDefaultModel
import com.metrolist.music.constants.DiscoverEveningRefreshKey
import com.metrolist.music.constants.DiscoverGeminiApiKey
import com.metrolist.music.constants.DiscoverModelKey
import com.metrolist.music.constants.DiscoverMorningRefreshKey
import com.metrolist.music.constants.DiscoverWebSearchKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.discoverLabel
import com.metrolist.music.ui.component.sounLavenderInk
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.SounAccent
import com.metrolist.music.ui.theme.SounGlassDialog
import com.metrolist.music.ui.theme.sounPanel
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.DayPeriod
import com.metrolist.music.utils.DiscoverError
import com.metrolist.music.utils.DiscoverEveningTime
import com.metrolist.music.utils.DiscoverMorningTime
import com.metrolist.music.utils.DiscoverPage
import com.metrolist.music.utils.DiscoverSection
import com.metrolist.music.utils.DiscoverSong
import com.metrolist.music.utils.DiscoverStep
import com.metrolist.music.utils.SounDiscover
import com.metrolist.music.utils.SounDiscoverJobService
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Date

private val OnAccent = Color(0xFF17121F)
private val PanelShape = RoundedCornerShape(28.dp)

/* The page ---------------------------------------------------------------------------------- */

@Composable
fun SounDiscoverScreen() {
    val nav = LocalNavController.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by remember { SounDiscover.state(context) }.collectAsStateWithLifecycle()
    val apiKey by rememberPreference(DiscoverGeminiApiKey, "")
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val page = state.page

    // A missing or old page is made as soon as the page is opened.
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank() && !state.refreshing && SounDiscover.isStale(state.page)) {
            SounDiscover.refreshInBackground(context)
        }
    }

    fun play(songs: List<DiscoverSong>, title: String, start: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val items = (if (shuffle) songs.shuffled() else songs).map { it.toMediaMetadata().toMediaItem() }
        playerConnection.playQueue(ListQueue(title = title, items = items, startIndex = if (shuffle) 0 else start))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top).asPaddingValues()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
        ) {
            IconButton(onClick = { nav.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.soun_discover), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    page?.let { subtitle(context, it) } ?: stringResource(R.string.soun_discover_tagline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (apiKey.isNotBlank()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    if (state.refreshing) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = SounAccent, modifier = Modifier.size(22.dp))
                    } else {
                        IconButton(onClick = { SounDiscover.refreshInBackground(context) }) {
                            Icon(painterResource(R.drawable.refresh), stringResource(R.string.soun_discover_refresh))
                        }
                    }
                }
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(painterResource(R.drawable.settings), stringResource(R.string.soun_discover_settings))
            }
        }

        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                apiKey.isBlank() -> item(key = "setup") { SetupPanel(onSetUp = { showSettings = true }) }
                page == null && state.refreshing -> item(key = "progress") { ProgressPanel(state.step) }
                page == null -> item(key = "error") { ErrorPanel(state.error, onRetry = { SounDiscover.refreshInBackground(context) }, onSettings = { showSettings = true }) }
                else -> {
                    item(key = "intro") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            if (page.intro.isNotBlank()) {
                                Text(page.intro, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Pill(stringResource(R.string.soun_discover_play_all), R.drawable.play, primary = true, modifier = Modifier.weight(1f)) {
                                    play(page.songs, context.getString(R.string.soun_discover))
                                }
                                Pill(stringResource(R.string.shuffle), R.drawable.shuffle, primary = false, modifier = Modifier.weight(1f)) {
                                    play(page.songs, context.getString(R.string.soun_discover), shuffle = true)
                                }
                            }
                            // A refresh running over the current page, or one that failed.
                            AnimatedVisibility(state.refreshing || state.error != null) {
                                Text(
                                    if (state.refreshing) stepText(state.step) else errorText(state.error),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (state.refreshing) sounLavenderInk() else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    itemsIndexed(page.sections, key = { index, section -> "section_${index}_${section.title}" }) { _, section ->
                        SectionPanel(
                            section = section,
                            currentId = mediaMetadata?.id,
                            onPlay = { start -> play(section.picks.map { it.song }, section.title, start) },
                            onMenu = { song ->
                                menuState.show { YouTubeSongMenu(song = song.toSongItem(), onDismiss = menuState::dismiss) }
                            },
                        )
                    }
                    page.searchSuggestionsHtml?.let { html ->
                        item(key = "search_suggestions") { SearchSuggestions(html) }
                    }
                    item(key = "made_by") {
                        Text(
                            stringResource(R.string.soun_discover_made_by, page.model),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showSettings) DiscoverSettingsDialog(onDismiss = { showSettings = false })
}

private fun subtitle(context: Context, page: DiscoverPage): String {
    val period = runCatching { DayPeriod.valueOf(page.period) }.getOrNull()
    val time = DateFormat.getTimeFormat(context).format(Date(page.generatedAt))
    return if (period != null) {
        context.getString(R.string.soun_discover_subtitle, context.getString(period.discoverLabel()), time)
    } else {
        context.getString(R.string.soun_discover_updated, time)
    }
}

@Composable
private fun stepText(step: DiscoverStep?): String =
    stringResource(
        when (step) {
            DiscoverStep.PROFILE, null -> R.string.soun_discover_step_profile
            DiscoverStep.ASKING -> R.string.soun_discover_step_asking
            DiscoverStep.FINDING -> R.string.soun_discover_step_finding
        },
    )

@Composable
private fun errorText(error: DiscoverError?): String =
    stringResource(
        when (error) {
            DiscoverError.NO_KEY -> R.string.soun_discover_error_no_key
            DiscoverError.BAD_KEY -> R.string.soun_discover_error_bad_key
            DiscoverError.BAD_MODEL -> R.string.soun_discover_error_bad_model
            DiscoverError.QUOTA -> R.string.soun_discover_error_quota
            DiscoverError.NETWORK -> R.string.soun_discover_error_network
            DiscoverError.BAD_REPLY -> R.string.soun_discover_error_bad_reply
            DiscoverError.NOTHING_FOUND -> R.string.soun_discover_error_nothing_found
            DiscoverError.SERVICE, null -> R.string.soun_discover_error_service
        },
    )

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .sounPanel(PanelShape)
                .padding(18.dp),
    ) { content() }
}

@Composable
private fun PanelIcon(@DrawableRes icon: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(SounAccent.copy(alpha = 0.16f)),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = sounLavenderInk(), modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun SetupPanel(onSetUp: () -> Unit) {
    Panel {
        PanelIcon(R.drawable.soun_discover)
        Text(stringResource(R.string.soun_discover_setup_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.soun_discover_setup_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Pill(stringResource(R.string.soun_discover_setup_action), R.drawable.key, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onSetUp)
    }
}

@Composable
private fun ProgressPanel(step: DiscoverStep?) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(strokeWidth = 3.dp, color = SounAccent, modifier = Modifier.size(28.dp))
            Text(stepText(step), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.soun_discover_progress_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorPanel(
    error: DiscoverError?,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
) {
    Panel {
        PanelIcon(R.drawable.error)
        Text(errorText(error), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(stringResource(R.string.soun_discover_retry), R.drawable.refresh, primary = true, modifier = Modifier.weight(1f), onClick = onRetry)
            Pill(stringResource(R.string.soun_discover_settings_short), R.drawable.settings, primary = false, modifier = Modifier.weight(1f), onClick = onSettings)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionPanel(
    section: DiscoverSection,
    currentId: String?,
    onPlay: (Int) -> Unit,
    onMenu: (DiscoverSong) -> Unit,
) {
    Panel {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (section.reason.isNotBlank()) {
                    Text(section.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SounAccent)
                        .clickable(onClickLabel = stringResource(R.string.play)) { onPlay(0) },
            ) {
                Icon(painterResource(R.drawable.play), stringResource(R.string.play), tint = OnAccent, modifier = Modifier.size(22.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            section.picks.forEachIndexed { index, pick ->
                val active = pick.song.id == currentId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) SounAccent.copy(alpha = 0.12f) else Color.Transparent)
                            .combinedClickable(onClick = { onPlay(index) }, onLongClick = { onMenu(pick.song) })
                            .padding(6.dp),
                ) {
                    AsyncImage(
                        model = pick.song.thumbnailUrl?.resize(240, 240),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            pick.song.title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) sounLavenderInk() else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pick.song.artistNames,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pick.why.isNotBlank()) {
                            Text(
                                pick.why,
                                style = MaterialTheme.typography.labelMedium,
                                color = sounLavenderInk(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onMenu(pick.song) }) {
                        Icon(
                            painterResource(R.drawable.more_vert),
                            stringResource(R.string.soun_discover_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

/** Google's search suggestions, which Google asks to be shown with any answer that used its search. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SearchSuggestions(html: String) {
    Panel {
        Text(
            stringResource(R.string.soun_discover_search_suggestions),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    settings.javaScriptEnabled = false
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                return true
                            }
                        }
                }
            },
            update = { view ->
                if (view.tag != html) {
                    view.tag = html
                    view.loadDataWithBaseURL(
                        null,
                        "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>" +
                            "<body style=\"margin:0;background:transparent\">$html</body></html>",
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(112.dp),
        )
    }
}

@Composable
private fun Pill(
    text: String,
    @DrawableRes icon: Int,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(if (primary) SounAccent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .then(
                    if (primary) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(50)),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (primary) OnAccent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(text, color = if (primary) OnAccent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/* Settings ---------------------------------------------------------------------------------- */

private val SuggestedModels = listOf(DiscoverDefaultModel to true, "gemini-2.5-flash-lite" to true, "gemini-3.6-flash" to false)

@Composable
fun DiscoverSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedKey by rememberPreference(DiscoverGeminiApiKey, "")
    val savedModel by rememberPreference(DiscoverModelKey, DiscoverDefaultModel)
    val savedSearch by rememberPreference(DiscoverWebSearchKey, true)
    val savedMorning by rememberPreference(DiscoverMorningRefreshKey, true)
    val savedEvening by rememberPreference(DiscoverEveningRefreshKey, true)

    var apiKey by remember(savedKey) { mutableStateOf(savedKey) }
    var model by remember(savedModel) { mutableStateOf(savedModel) }
    var webSearch by remember(savedSearch) { mutableStateOf(savedSearch) }
    var morning by remember(savedMorning) { mutableStateOf(savedMorning) }
    var evening by remember(savedEvening) { mutableStateOf(savedEvening) }
    var showKey by remember { mutableStateOf(false) }

    fun saveAndClose() {
        val key = apiKey.trim()
        val chosenModel = model.trim().ifBlank { DiscoverDefaultModel }
        val changed = key != savedKey || chosenModel != savedModel || webSearch != savedSearch
        scope.launch {
            withContext(Dispatchers.IO) {
                context.dataStore.edit {
                    it[DiscoverGeminiApiKey] = key
                    it[DiscoverModelKey] = chosenModel
                    it[DiscoverWebSearchKey] = webSearch
                    it[DiscoverMorningRefreshKey] = morning
                    it[DiscoverEveningRefreshKey] = evening
                }
                SounDiscoverJobService.schedule(context.applicationContext)
            }
            if (changed && key.isNotBlank()) SounDiscover.refreshInBackground(context)
        }
        onDismiss()
    }

    SounGlassDialog(onDismiss = ::saveAndClose) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState()).padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PanelIcon(R.drawable.soun_discover)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.soun_discover_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(R.string.soun_discover_settings_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.soun_discover_api_key)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            painterResource(if (showKey) R.drawable.lock_open else R.drawable.lock),
                            stringResource(if (showKey) R.string.soun_discover_hide_key else R.string.soun_discover_show_key),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.soun_discover_get_key),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = sounLavenderInk(),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
                            }
                        }.padding(vertical = 4.dp),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.soun_discover_model)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SuggestedModels.forEach { (name, free) ->
                    val selected = model.trim() == name
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) SounAccent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .clickable { model = name }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(name.removePrefix("gemini-"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (free) R.string.soun_discover_model_free else R.string.soun_discover_model_paid),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingSwitch(stringResource(R.string.soun_discover_web_search), stringResource(R.string.soun_discover_web_search_desc), webSearch) { webSearch = it }
            SettingSwitch(stringResource(R.string.soun_discover_refresh_at, timeLabel(context, DiscoverMorningTime)), null, morning) { morning = it }
            SettingSwitch(stringResource(R.string.soun_discover_refresh_at, timeLabel(context, DiscoverEveningTime)), null, evening) { evening = it }

            Text(
                stringResource(R.string.soun_discover_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Pill(stringResource(R.string.soun_sleep_done), R.drawable.check, primary = true, modifier = Modifier.fillMaxWidth(), onClick = ::saveAndClose)
        }
    }
}

private fun timeLabel(context: Context, time: LocalTime): String =
    DateFormat.getTimeFormat(context).format(
        Date.from(time.atDate(java.time.LocalDate.now()).atZone(java.time.ZoneId.systemDefault()).toInstant()),
    )

@Composable
private fun SettingSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SounAccent, checkedThumbColor = OnAccent),
        )
    }
}
