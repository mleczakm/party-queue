package pl.mleczki.partyqueue

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.IntentFilter
import android.media.AudioManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BROWSE_TAB = 3

/** The title bar hides after this long without a touch. */
private const val HEADER_HIDE_MS = 8_000L

/** How large the YouTube preview of the playing song is. */
private enum class Preview(val height: androidx.compose.ui.unit.Dp?) {
    Hidden(1.dp), Normal(230.dp), Full(null);

    fun next() = entries[(ordinal + 1) % entries.size]
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

// =====================================================================================================================
// Root
// =====================================================================================================================

@Composable
fun HostApp(app: PartyApp, themeMode: ThemeMode, initialTab: Int = 0, initialPreview: Int = 0) {
    val party = app.party
    // The snapshot changes every second (playback position). Only [NowPlaying] may read it directly;
    // everything else reads a slice through derivedStateOf so it recomposes only when its slice changes.
    val state = party.state.collectAsStateWithLifecycle()
    val notice by remember { derivedStateOf { state.value.notice } }
    val playing by remember { derivedStateOf { state.value.player.status == "playing" } }
    val proposalCount by remember { derivedStateOf { state.value.proposals.size } }
    val requestCount by remember { derivedStateOf { state.value.guests.count { it.hostRequested } } }
    val visible by app.player.visibleSession.collectAsStateWithLifecycle()
    // The song's video is rarely needed at a party: hidden until the host asks for it.
    var preview by rememberSaveable { mutableStateOf(Preview.entries[initialPreview.coerceIn(0, 2)]) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val full = preview == Preview.Full

    // The title bar gets out of the way after a while; dragging down anywhere (or the little handle) brings it back.
    var headerShown by rememberSaveable { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(headerShown) {
        while (headerShown) {
            delay(1_000)
            if (System.currentTimeMillis() - lastTouch > HEADER_HIDE_MS) headerShown = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                // Watches only; every touch still reaches the controls below.
                val reveal = 56.dp.toPx()
                awaitPointerEventScope {
                    var pulled = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                        val c = event.changes.firstOrNull() ?: continue
                        if (!c.pressed) { pulled = 0f; continue }
                        if (c.previousPressed) pulled = (pulled + c.positionChange().y).coerceAtLeast(0f) else pulled = 0f
                        if (pulled > reveal) { headerShown = true; pulled = 0f }
                    }
                }
            }
    ) {
        PartyHeader(themeMode, playing, headerShown, onReveal = { headerShown = true; lastTouch = System.currentTimeMillis() }) { app.cycleTheme() }

        // Never under the navigation bar or a camera cut-out; the page itself must also stay clear of the bars.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        ) {
            // While browsing YouTube the player is parked in a 1dp strip: it keeps playing, but the screen
            // shows a single page instead of two stacked ones.
            val paneHeight = when {
                tab == BROWSE_TAB && !full -> Preview.Hidden.height!!
                else -> preview.height
            }
            Box(if (paneHeight == null) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(paneHeight)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> GeckoView(ctx).also { it.setSession(visible); app.player.onViewAttached() } },
                    // The two player pages take turns; show whichever one is playing.
                    update = { view -> if (view.session !== visible) { view.releaseSession(); view.setSession(visible) } },
                    onRelease = { it.releaseSession() },
                )
            }
            NowPlaying(state, party, preview) { preview = it }
            BatteryBanner()
            AnimatedVisibility(
                visible = notice != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                var last by remember { mutableStateOf("") }
                notice?.let { last = it }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(last, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { party.dismissNotice() }) { Text("OK") }
                }
            }
            if (!full) {
                PartyTabs(
                    labels = listOf("Kolejka", "Propozycje", "Zaproś", "YouTube"),
                    badges = listOf(0, proposalCount, requestCount, 0),
                    selected = tab,
                    onSelect = { tab = it },
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            // A web page does not take well to being faded; switch to and from it at once.
                            if (targetState == BROWSE_TAB || initialState == BROWSE_TAB) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(120))
                            }
                        },
                        label = "tab",
                    ) { target ->
                        when (target) {
                            0 -> QueueTab(state, party)
                            1 -> ProposalsTab(state, party)
                            2 -> InviteTab(state, party, app.server.port)
                            else -> BrowseTab(app, party)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================================================
// Header, tabs, now playing
// =====================================================================================================================

@Composable
private fun PartyHeader(mode: ThemeMode, playing: Boolean, shown: Boolean, onReveal: () -> Unit, onToggleTheme: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Brand.Pink, Brand.Violet, Brand.Blue)))
    ) {
        // The page below must never slide under the status bar, so its strip stays even when the title is away.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        AnimatedVisibility(shown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier.padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 34.dp, bars = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE3F3))), spark = Brand.Lime)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Party Queue", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.3).sp)
                    Text("kolejka na każdą imprezę", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
                EqualizerBars(playing, size = 22.dp, brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE3F3))))
                Spacer(Modifier.width(6.dp))
                RoundButton(onClick = onToggleTheme, size = 40.dp) {
                    when (mode) {
                        ThemeMode.AUTO -> AutoGlyph(Color.White)
                        ThemeMode.LIGHT -> SunGlyph(Color.White)
                        ThemeMode.DARK -> MoonGlyph(Color.White)
                    }
                }
            }
        }
        AnimatedVisibility(!shown, enter = fadeIn(), exit = fadeOut()) {
            // A small handle: tap it or pull down to bring the title back.
            Box(
                Modifier.fillMaxWidth().height(14.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onReveal),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.75f)))
            }
        }
    }
}

@Composable
private fun PartyTabs(labels: List<String>, badges: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        val tabWidth = maxWidth / labels.size
        val indicatorX by animateDpAsState(tabWidth * selected + tabWidth * 0.18f, spring(stiffness = 500f), label = "tabx")
        Column {
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    val on = i == selected
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onSelect(i) }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (badges[i] > 0) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                Modifier.size(18.dp).clip(CircleShape).background(Brand.Pink),
                                contentAlignment = Alignment.Center,
                            ) { Text("${badges[i]}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Box(Modifier.height(3.dp).fillMaxWidth()) {
                Box(
                    Modifier
                        .offset(x = indicatorX)
                        .width(tabWidth * 0.64f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Brand.horizontal())
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlaying(state: State<Snapshot>, party: PartyController, preview: Preview, setPreview: (Preview) -> Unit) {
    val s = state.value
    val cur = s.current
    val playing = s.player.status == "playing"
    val fraction by animateFloatAsState(
        if (s.player.durMs > 0) (s.player.posMs.toFloat() / s.player.durMs).coerceIn(0f, 1f) else 0f,
        tween(900, easing = LinearEasing),
        label = "progress",
    )
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .flowingGradient(listOf(Brand.Pink, Brand.Violet, Brand.Blue, Brand.Violet), RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    if (cur != null) {
                        AsyncImage(cur.thumb(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        BrandMark(size = 34.dp, bars = Brush.verticalGradient(listOf(Color.White, Color(0xFFFFE3F3))), spark = Brand.Lime)
                    }
                    if (cur != null) {
                        Box(Modifier.align(Alignment.BottomStart).padding(5.dp).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(4.dp)) {
                            EqualizerBars(playing, size = 14.dp, brush = Brush.verticalGradient(listOf(Color.White, Brand.Lime)))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        cur?.title ?: "Nic nie gra",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_500, repeatDelayMillis = 2_000, spacing = MarqueeSpacing(40.dp), velocity = 28.dp),
                    )
                    Text(
                        when {
                            cur == null -> "Ustaw playlistę w karcie YouTube"
                            s.player.status == "loading" -> "Ładowanie…"
                            s.player.status == "ad" -> "Reklama"
                            else -> cur.addedBy.let { if (cur.source == Source.PLAYLIST) cur.channel else "od: $it" }.ifBlank { "Party Queue" }
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmt(s.player.posMs), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color.White, Brand.Lime))))
                }
                Spacer(Modifier.width(8.dp))
                Text(fmt(s.player.durMs), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(onClick = { party.previous() }, size = 42.dp) { SkipGlyph(Color.White, previous = true) }
                Spacer(Modifier.width(10.dp))
                RoundButton(
                    onClick = { party.togglePlay() },
                    size = 54.dp,
                    background = Brush.linearGradient(listOf(Color.White, Color(0xFFFFE3F3))),
                ) { if (playing) PauseGlyph(Brand.Violet, 26.dp) else PlayGlyph(Brand.Violet, 26.dp) }
                Spacer(Modifier.width(10.dp))
                RoundButton(onClick = { party.skip() }, size = 42.dp) { SkipGlyph(Color.White, previous = false) }
                Spacer(Modifier.weight(1f))
                // One button, three states: no preview (crossed-out screen), small preview, full screen.
                RoundButton(onClick = { setPreview(preview.next()) }, size = 42.dp) { ScreenGlyph(Color.White, preview) }
            }
            Spacer(Modifier.height(2.dp))
            VolumeControl()
        }
    }
}

/** The phone's media volume (the same one the volume keys change); follows the keys too. */
@Composable
private fun VolumeControl() {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var level by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var beforeMute by remember { mutableIntStateOf(max / 2) }
    LifecycleResumeEffect(Unit) { level = audio.getStreamVolume(AudioManager.STREAM_MUSIC); onPauseOrDispose { } }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                level = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    fun set(v: Int) {
        level = v.coerceIn(0, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Tap the speaker to mute, and again to get the old volume back.
        RoundButton(
            onClick = { if (level > 0) { beforeMute = level; set(0) } else set(beforeMute.coerceAtLeast(1)) },
            size = 34.dp,
        ) { VolumeGlyph(Color.White, level.toFloat() / max) }
        Slider(
            value = level.toFloat(),
            onValueChange = { set(it.roundToInt()) },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f).height(36.dp).padding(start = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        Text("${(level * 100f / max).roundToInt()}%", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
    }
}

/** Without this exemption Android may freeze playback and the guest server once the screen is off. */
@Composable
private fun BatteryBanner() {
    val ctx = LocalContext.current
    var ignoring by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        ignoring = ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        onPauseOrDispose { }
    }
    AnimatedVisibility(!ignoring, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Żeby muzyka grała po wygaszeniu ekranu, pozwól aplikacji działać w tle.",
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${ctx.packageName}"),
                    )
                )
            }) { Text("Pozwól") }
        }
    }
}

// =====================================================================================================================
// Song rows
// =====================================================================================================================

@Composable
private fun Thumb(videoId: String, width: androidx.compose.ui.unit.Dp = 72.dp, height: androidx.compose.ui.unit.Dp = 40.dp) {
    AsyncImage(
        model = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = width, height = height).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** A song card. The title stays on one line and, when it is too long, slowly scrolls so it can be read in full. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCard(
    title: String,
    subtitle: String,
    videoId: String?,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.height(IntrinsicSizeMinCompat), verticalAlignment = Alignment.CenterVertically) {
            if (accent) Box(Modifier.width(5.dp).fillMaxHeight().background(Brand.diagonal()))
            Row(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (videoId != null) {
                    Thumb(videoId)
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1_500, repeatDelayMillis = 2_000, spacing = MarqueeSpacing(40.dp), velocity = 28.dp),
                    )
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                actions()
            }
        }
    }
}

private val IntrinsicSizeMinCompat = 56.dp

private val SwipeGreen = Color(0xFF1FA85A)
private val SwipeRed = Color(0xFFE5384F)

/** A row that can be dragged: right plays it now (green), left removes it (red). [hint] > 0 makes it wiggle to show how. */
@Composable
private fun SwipeRow(
    hint: Int,
    showHint: Boolean,
    onPlayNow: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    gesture: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val peek = with(LocalDensity.current) { 76.dp.toPx() }

    LaunchedEffect(hint) {
        if (hint > 0 && showHint) {
            for (direction in listOf(1f, -1f)) {
                offset.animateTo(direction * peek, tween(420))
                delay(260)
                offset.animateTo(0f, tween(360))
                delay(120)
            }
        }
    }

    fun settle() = scope.launch {
        val threshold = width * 0.35f
        when {
            offset.value > threshold -> { offset.animateTo(width, tween(160)); onPlayNow() }
            offset.value < -threshold -> { offset.animateTo(-width, tween(160)); onRemove() }
            else -> offset.animateTo(0f, spring(stiffness = 450f))
        }
    }

    val progress = (abs(offset.value) / (width * 0.3f)).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).onSizeChanged { width = it.width.toFloat() }) {
        if (offset.value != 0f) {
            val playing = offset.value > 0
            Box(
                Modifier.matchParentSize().background(if (playing) SwipeGreen else SwipeRed).padding(horizontal = 24.dp),
                contentAlignment = if (playing) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (playing) Icons.Filled.PlayArrow else Icons.Filled.Delete,
                    contentDescription = if (playing) "Graj teraz" else "Usuń z kolejki",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp).scale(0.8f + 0.4f * progress).alpha(0.45f + 0.55f * progress),
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { offset.stop() } },
                        onDragEnd = { settle() },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(200)) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + drag).coerceIn(-width, width)) }
                    }
                }
                // Inner modifier: it sees a touch first, so a long press can claim it before the sideways swipe does.
                .then(gesture)
        ) { content() }
    }
}

/**
 * One finger on a row: a short tap, or a long press followed by a drag. Anything else (a sideways swipe, a scroll)
 * is left alone for the other detectors.
 */
private suspend fun PointerInputScope.detectTapOrLongDrag(
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val longPress = awaitLongPressOrCancellation(down.id)
    if (longPress != null) {
        onDragStart()
        drag(longPress.id) { change ->
            change.consume()
            onDrag(change.positionChange().y)
        }
        onDragEnd()
    } else {
        val change = currentEvent.changes.firstOrNull { it.id == down.id }
        if (change != null && !change.pressed && !change.isConsumed) onTap()
    }
}

// =====================================================================================================================
// Tabs
// =====================================================================================================================

private const val IDLE_HINT_MS = 45_000L
private const val HINT_REPEAT_MS = 180_000L

@Composable
private fun QueueTab(state: State<Snapshot>, party: PartyController) {
    val queue by remember { derivedStateOf { state.value.queue } }
    val repeat by remember { derivedStateOf { state.value.repeat } }
    val ctx = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hint by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var draggedUid by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    // Rarely, and only when nobody touches the list: the first row slides sideways to show what the gestures do.
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            val now = System.currentTimeMillis()
            if (queue.isNotEmpty() && draggedUid == null && now - lastTouch > IDLE_HINT_MS) {
                hint++
                lastTouch = now + HINT_REPEAT_MS - IDLE_HINT_MS
            }
        }
    }

    // Holding a row near the top or bottom edge scrolls the list so it can be carried a long way.
    LaunchedEffect(draggedUid) {
        val uid = draggedUid ?: return@LaunchedEffect
        while (draggedUid == uid) {
            val info = listState.layoutInfo
            val cur = info.visibleItemsInfo.firstOrNull { it.key == uid }
            if (cur != null) {
                val centre = cur.offset + dragDelta + cur.size / 2f
                val edge = 110f
                val step = when {
                    centre < info.viewportStartOffset + edge -> -22f
                    centre > info.viewportEndOffset - edge -> 22f
                    else -> 0f
                }
                if (step != 0f) dragDelta += listState.scrollBy(step)
            }
            delay(16)
        }
    }

    /** One finger move: swap the dragged row with the neighbour it has been carried over, one slot at a time. */
    fun dragBy(uid: String, dy: Float) {
        dragDelta += dy
        val rows = listState.layoutInfo.visibleItemsInfo.map { RowInfo(it.key, it.index, it.offset, it.size) }
        val swap = nextSwap(rows, queue.map { it.uid }, uid, dragDelta) ?: return
        party.move(uid, swap.delta)
        dragDelta += swap.shift
    }

    if (queue.isEmpty()) {
        EmptyState(
            title = "Kolejka jest pusta",
            text = "Otwórz kartę YouTube, wybierz playlistę i ustaw ją jako aktualną. Goście też mogą proponować utwory.",
        )
        return
    }
    Column(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    lastTouch = System.currentTimeMillis()
                }
            }
        }
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("W kolejce: ${queue.size}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Dotknij: na górę · w prawo: graj · w lewo: usuń · przytrzymaj i przesuń: kolejność",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Powtarzaj", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(repeat, { party.setRepeat(it) }, Modifier.scale(0.7f))
                }
                TextButton(
                    onClick = { confirmClear = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Wyczyść", fontSize = 13.sp) }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(queue, key = { _, t -> t.uid }) { index, t ->
                val sub = listOfNotNull(
                    if (index == 0) "NASTĘPNA" else null,
                    t.channel.ifBlank { null },
                    t.duration,
                    if (t.source == Source.PLAYLIST) null else "dodał(a) ${t.addedBy}",
                ).joinToString(" · ")
                val dragged = draggedUid == t.uid
                SwipeRow(
                    hint, showHint = index == 0,
                    onPlayNow = { party.playNow(t.uid) },
                    onRemove = { party.remove(t.uid) },
                    modifier = Modifier
                        .then(if (dragged) Modifier else Modifier.animateItem())
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragged) dragDelta else 0f
                            scaleX = if (dragged) 1.03f else 1f
                            scaleY = if (dragged) 1.03f else 1f
                            shadowElevation = if (dragged) 28f else 0f
                        },
                    gesture = Modifier.pointerInput(t.uid) {
                        detectTapOrLongDrag(
                            onTap = {
                                if (index > 0) {
                                    party.moveToFront(t.uid)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    toast(ctx, "Na górze kolejki: ${t.title}")
                                }
                            },
                            onDragStart = {
                                draggedUid = t.uid
                                dragDelta = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { dragBy(t.uid, it) },
                            onDragEnd = { draggedUid = null; dragDelta = 0f },
                        )
                    },
                ) {
                    TrackCard(t.title, sub, t.videoId, accent = index == 0)
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Wyczyścić kolejkę?") },
            text = { Text("Usunie wszystkie utwory z kolejki (${queue.size}). Utwór, który teraz gra, dokończy się.") },
            confirmButton = { TextButton(onClick = { party.clearQueue(); confirmClear = false }) { Text("Wyczyść") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(size = 72.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProposalsTab(state: State<Snapshot>, party: PartyController) {
    val proposals by remember { derivedStateOf { state.value.proposals } }
    if (proposals.isEmpty()) {
        EmptyState("Brak propozycji", "Gdy ktoś z gości zaproponuje utwór, pojawi się tutaj do zatwierdzenia.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(proposals, key = { _, p -> p.id }) { _, p ->
            TrackCard(p.meta.title, "${p.byName} · ${p.meta.channel}", p.meta.videoId, Modifier.animateItem()) {
                TextButton(onClick = { party.reject(p.id) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Odrzuć") }
                GradientButton("Zatwierdź", { party.approve(p.id) }, brush = Brush.horizontalGradient(listOf(Brand.Violet, Brand.Blue)))
            }
        }
    }
}

@Composable
private fun QrCode(text: String, modifier: Modifier) {
    // A QR code needs a clear border of about four modules to be read; it is part of the matrix here.
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
    }
    Canvas(modifier.background(Color.White)) {
        val cell = size.width / matrix.width
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            if (matrix[x, y]) drawRoundRect(Color(0xFF1B0B3A), Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f), CornerRadius(cell * 0.2f))
        }
    }
}

@Composable
private fun InviteTab(state: State<Snapshot>, party: PartyController, port: Int) {
    val joinOpen by remember { derivedStateOf { state.value.joinOpen } }
    val guests by remember { derivedStateOf { state.value.guests } }
    val online by remember { derivedStateOf { state.value.online } }
    val ctx = LocalContext.current
    var addresses by remember { mutableStateOf(LocalNet.addresses()) }
    var selected by remember { mutableIntStateOf(0) }
    val ip = addresses.getOrNull(selected)
    val url = ip?.let { "http://$it:$port/" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (url == null) {
                    Text("Brak sieci lokalnej. Połącz się z Wi-Fi albo włącz hotspot.", textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                } else {
                    // The frame's gradient slowly flows around the code.
                    val transition = rememberInfiniteTransition(label = "qr")
                    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse), "t")
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .drawBehind {
                                val shift = size.width * t
                                drawRect(Brush.linearGradient(Brand.Gradient + Brand.Orange, Offset(-shift, 0f), Offset(size.width * 1.5f - shift, size.height)))
                            }
                            .padding(6.dp)
                    ) {
                        QrCode(url, Modifier.size(250.dp).clip(RoundedCornerShape(22.dp)))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(url, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Goście muszą być w tej samej sieci Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (addresses.size > 1) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        addresses.forEachIndexed { i, a ->
                            val on = i == selected
                            Text(
                                a,
                                fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) Brush.horizontalGradient(Brand.Gradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                    .clickable { selected = i }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
        if (url != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                    GradientButton("Udostępnij link", {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Dołącz do imprezy i dodaj swoją piosenkę: $url")
                        }
                        ctx.startActivity(Intent.createChooser(send, "Zaproś gości"))
                    })
                    OutlinedButton(onClick = {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Party Queue", url))
                        toast(ctx, "Skopiowano link")
                    }) { Text("Kopiuj") }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Przyjmuj nowych gości", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (joinOpen) "Każdy, kto otworzy stronę, może dołączyć." else "Nowi goście nie wejdą; obecni zostają.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(joinOpen, { party.setJoinOpen(it) })
                }
            }
        }
        item { SectionTitle("Goście (${guests.size})") }
        if (guests.isEmpty()) item { Text("Nikt jeszcze nie dołączył.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(guests, key = { _, g -> g.id }) { _, g ->
            Surface(Modifier.animateItem(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (g.id in online) Brand.Lime else MaterialTheme.colorScheme.outline))
                        Spacer(Modifier.width(8.dp))
                        Text(g.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when {
                                g.role == Role.HOST -> "współhost"
                                g.hostRequested -> "prosi o uprawnienia"
                                else -> "gość"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (g.hostRequested) Brand.Pink else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (g.hostRequested) {
                            GradientButton("Zatwierdź", { party.approveHost(g.id) })
                            OutlinedButton(onClick = { party.rejectHost(g.id) }) { Text("Odrzuć") }
                        }
                        // The host can promote anyone straight away; the guest's page switches to host mode by itself.
                        if (g.role == Role.GUEST && !g.hostRequested) OutlinedButton(onClick = { party.approveHost(g.id) }) { Text("Nadaj uprawnienia hosta") }
                        if (g.role == Role.HOST) OutlinedButton(onClick = { party.demote(g.id) }) { Text("Odbierz host") }
                        TextButton(onClick = { party.kick(g.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Usuń") }
                    }
                }
            }
        }
    }
}

/** A real YouTube page the host can browse; the bar below offers only what makes sense for the page showing. */
@Composable
private fun BrowseTab(app: PartyApp, party: PartyController) {
    val bridge = app.player
    val url by bridge.browseUrl.collectAsStateWithLifecycle()
    val signedIn by bridge.signedIn.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Playlists are added in the order YouTube shows them; shuffling is a deliberate choice.
    var shuffle by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }

    val listId = YouTubeClient.parsePlaylistId(url)
    val videoId = YouTubeClient.parseVideoId(url).takeIf { "/watch" in url || "/shorts/" in url }
    val isMix = listId?.startsWith("RD") == true

    fun work(block: suspend () -> String) {
        scope.launch {
            busy = true
            message = null
            message = try { block() } catch (e: Exception) { e.message ?: "Błąd" }
            busy = false
        }
    }

    fun setPlaylist(id: String) = work {
        if (isMix) {
            // A Mix exists only on a watch page: read its songs from the video that is showing.
            val fromVideo = YouTubeClient.parseVideoId(url).takeIf { "/watch" in url }
                ?: throw PartyException("Otwórz dowolny film z tego miksu, a potem ustaw go jako playlistę")
            try {
                party.loadWatchPlaylist(fromVideo, id, shuffle)
            } catch (e: Exception) {
                // Personal mixes (e.g. "Mój mix") need the signed-in page.
                party.importPlaylistData(url, bridge.scrapePlaylist(url), shuffle)
            }
        } else {
            val page = "https://m.youtube.com/playlist?list=$id"
            try {
                party.loadPlaylist(page, shuffle)
            } catch (e: Exception) {
                // Private or sign-in-only list: read it from the page this browser sees.
                party.importPlaylistData(page, bridge.scrapePlaylist(page), shuffle)
            }
        }
        "Aktualna playlista: „${party.state.value.playlistTitle}”. W kolejce: ${party.state.value.queue.size}."
    }

    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx -> GeckoView(ctx).also { it.setSession(bridge.browseSession()) } },
            onRelease = { it.releaseSession() },
        )
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (listId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(shuffle, { shuffle = it }, Modifier.scale(0.85f))
                        Text("Losowa kolejność", style = MaterialTheme.typography.bodySmall)
                    }
                    GradientButton("Ustaw jako aktualną playlistę", { setPlaylist(listId) }, Modifier.fillMaxWidth(), enabled = !busy)
                    if (isMix) {
                        Text(
                            "Mix: trafią do kolejki utwory widoczne teraz w mixie (zwykle ok. 25).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (videoId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GradientButton("Dodaj do kolejki", {
                            work { party.enqueue(party.yt.meta(videoId), "Host", Source.HOST, atEnd = true); "Dodano na koniec kolejki" }
                        }, Modifier.weight(1f), enabled = !busy)
                        OutlinedButton(onClick = {
                            work { party.enqueue(party.yt.meta(videoId), "Host", Source.HOST, playNext = true); "Zagra jako następny" }
                        }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Jako następny") }
                    }
                }
                if (listId == null && videoId == null) {
                    Text(
                        "Znajdź playlistę lub film. Na stronie playlisty pojawi się przycisk ustawienia jej jako aktualnej.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(message != null) {
                    Text(message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pasteOpen = true }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Wklej link") }
                    if (!signedIn) {
                        TextButton(
                            onClick = { bridge.browseTo("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F") },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("Zaloguj się, aby dodać prywatne playlisty", fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("Wklej link") },
            text = {
                Column {
                    Text("Link do filmu albo do publicznej playlisty z YouTube.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(pasted, { pasted = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("youtube.com/…") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val link = pasted
                    pasteOpen = false
                    pasted = ""
                    work {
                        val list = YouTubeClient.parsePlaylistId(link)
                        val vid = YouTubeClient.parseVideoId(link)
                        when {
                            list != null && !list.startsWith("RD") -> {
                                party.loadPlaylist(link, shuffle)
                                "Aktualna playlista: „${party.state.value.playlistTitle}”."
                            }
                            vid != null -> { party.enqueue(party.yt.meta(vid), "Host", Source.HOST, atEnd = true); "Dodano na koniec kolejki" }
                            else -> throw PartyException("To nie wygląda na link do filmu ani playlisty z YouTube")
                        }
                    }
                }, enabled = pasted.isNotBlank()) { Text("Dodaj") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Anuluj") } },
        )
    }
}
