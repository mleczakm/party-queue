package pl.mleczki.partyqueue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView

@Composable
fun PartyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}

private const val BROWSE_TAB = 4

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun HostApp(app: PartyApp, initialTab: Int = 0) {
    val party = app.party
    // The snapshot changes every second (playback position). Only [NowPlaying] may read it directly;
    // everything else reads a slice through derivedStateOf so it recomposes only when its slice changes.
    val state = party.state.collectAsStateWithLifecycle()
    val notice by remember { derivedStateOf { state.value.notice } }
    val proposalCount by remember { derivedStateOf { state.value.proposals.size } }
    val requestCount by remember { derivedStateOf { state.value.guests.count { it.hostRequested } } }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }

    // The page must never slide under the status bar or the gesture/navigation bar.
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // While browsing YouTube the player is parked in a 1dp strip: it keeps playing, but the screen
            // shows a single page instead of two stacked ones.
            Box(
                if (expanded) Modifier.fillMaxWidth().weight(1f)
                else Modifier.fillMaxWidth().height(if (tab == BROWSE_TAB) 1.dp else 230.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> GeckoView(ctx).also { it.setSession(app.player.session); app.player.onViewAttached() } },
                    onRelease = { it.releaseSession() },
                )
            }
            NowPlaying(state, party, expanded) { expanded = !expanded }
            BatteryBanner()
            notice?.let { n ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(n, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { party.dismissNotice() }) { Text("OK") }
                }
            }
            if (!expanded) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Kolejka") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                        BadgedBox(badge = { if (proposalCount > 0) Badge { Text("$proposalCount") } }) { Text("Propozycje") }
                    })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = {
                        BadgedBox(badge = { if (requestCount > 0) Badge { Text("$requestCount") } }) { Text("Dołącz") }
                    })
                    Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Dodaj") })
                    Tab(selected = tab == BROWSE_TAB, onClick = { tab = BROWSE_TAB }, text = { Text("YouTube") })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> QueueTab(state, party)
                        1 -> ProposalsTab(state, party)
                        2 -> JoinTab(state, party, app.server.port)
                        3 -> AddTab(state, party)
                        else -> BrowseTab(app, party)
                    }
                }
            }
        }
    }
}

/** Without this exemption Android may freeze playback and the guest server once the screen is off. */
@Composable
private fun BatteryBanner() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var ignoring by remember { mutableStateOf(true) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        ignoring = ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        onPauseOrDispose { }
    }
    if (ignoring) return
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "Żeby muzyka grała po wygaszeniu ekranu, pozwól aplikacji działać w tle.",
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = {
            ctx.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:${ctx.packageName}"),
                )
            )
        }) { Text("Pozwól") }
    }
}

@Composable
private fun NowPlaying(state: State<Snapshot>, party: PartyController, expanded: Boolean, toggleExpanded: () -> Unit) {
    val s = state.value
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        val cur = s.current
        Text(
            cur?.title ?: "Nic nie gra",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = when {
            cur == null -> "Dodaj utwór lub wczytaj playlistę"
            s.player.status == "loading" -> "Ładowanie…"
            s.player.status == "ad" -> "Reklama"
            else -> "${fmt(s.player.posMs)} / ${fmt(s.player.durMs)} · ${cur.addedBy}"
        }
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { party.previous() }) { Text("⏮") }
            TextButton(onClick = { party.togglePlay() }) { Text(if (s.player.status == "playing") "⏸" else "▶") }
            TextButton(onClick = { party.next() }) { Text("⏭") }
            Box(Modifier.weight(1f))
            TextButton(onClick = toggleExpanded) { Text(if (expanded) "Zmniejsz podgląd" else "Powiększ podgląd") }
        }
    }
}

@Composable
private fun TrackRow(title: String, subtitle: String, actions: @Composable RowScope.() -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
        HorizontalDivider()
    }
}

@Composable
private fun Mini(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) { Text(label) }
}

private val SwipeGreen = Color(0xFF2E7D32)
private val SwipeRed = Color(0xFFC62828)

/** A row that can be dragged: right plays it now (green), left removes it (red). [hint] > 0 makes it wiggle to show how. */
@Composable
private fun SwipeRow(
    hint: Int,
    hintIndex: Int,
    onPlayNow: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val peek = with(LocalDensity.current) { 76.dp.toPx() }

    LaunchedEffect(hint) {
        if (hint > 0 && hintIndex < 3) {
            delay(hintIndex * 220L)
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
            else -> offset.animateTo(0f, tween(220))
        }
    }

    val progress = (abs(offset.value) / (width * 0.3f)).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
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
                .background(MaterialTheme.colorScheme.surface)
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
        ) { content() }
    }
}

private const val IDLE_HINT_MS = 6_000L

@Composable
private fun QueueTab(state: State<Snapshot>, party: PartyController) {
    val queue by remember { derivedStateOf { state.value.queue } }
    var confirmClear by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hint by remember { mutableIntStateOf(0) }

    // When nobody touches the list for a while, the first rows slide sideways to show what the gestures do.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val now = System.currentTimeMillis()
            if (queue.isNotEmpty() && now - lastTouch > IDLE_HINT_MS) {
                hint++
                lastTouch = now + 10_000
            }
        }
    }

    if (queue.isEmpty()) {
        Text("Kolejka jest pusta.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("W kolejce: ${queue.size}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "W prawo: graj teraz · w lewo: usuń",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Wyczyść kolejkę") }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(queue, key = { _, t -> t.uid }) { index, t ->
                val sub = listOfNotNull(t.channel.ifBlank { null }, t.duration, if (t.source == Source.PLAYLIST) null else "dodał(a) ${t.addedBy}")
                    .joinToString(" · ")
                SwipeRow(hint, index, onPlayNow = { party.playNow(t.uid) }, onRemove = { party.remove(t.uid) }) {
                    TrackRow(t.title, sub) {
                        Mini("▲") { party.move(t.uid, -1) }
                        Mini("▼") { party.move(t.uid, 1) }
                    }
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
private fun ProposalsTab(state: State<Snapshot>, party: PartyController) {
    val proposals by remember { derivedStateOf { state.value.proposals } }
    if (proposals.isEmpty()) {
        Text("Brak propozycji od gości.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(proposals, key = { it.id }) { p ->
            TrackRow(p.meta.title, "${p.byName} · ${p.meta.channel}") {
                Mini("Odrzuć") { party.reject(p.id) }
                Button(onClick = { party.approve(p.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("Zatwierdź") }
            }
        }
    }
}

@Composable
private fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    }
    Canvas(modifier.background(Color.White)) {
        val cell = size.width / matrix.width
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

@Composable
private fun JoinTab(state: State<Snapshot>, party: PartyController, port: Int) {
    val joinSecret by remember { derivedStateOf { state.value.joinSecret } }
    val joinOpen by remember { derivedStateOf { state.value.joinOpen } }
    val guests by remember { derivedStateOf { state.value.guests } }
    val online by remember { derivedStateOf { state.value.online } }
    var addresses by remember { mutableStateOf(LocalNet.addresses()) }
    var selected by remember { mutableIntStateOf(0) }
    val ip = addresses.getOrNull(selected)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                if (ip == null) {
                    Text("Brak sieci lokalnej. Połącz się z Wi-Fi albo włącz hotspot.", Modifier.padding(16.dp))
                } else {
                    val url = "http://$ip:$port/?j=${joinSecret}"
                    QrCode(url, Modifier.padding(top = 12.dp).size(240.dp))
                    Text("http://$ip:$port", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text("Goście muszą być w tej samej sieci Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    addresses.forEachIndexed { i, a -> FilterChip(selected = i == selected, onClick = { selected = i }, label = { Text(a) }) }
                    TextButton(onClick = { addresses = LocalNet.addresses(); selected = 0 }) { Text("Odśwież") }
                }
            }
        }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Przyjmuj nowych gości", Modifier.weight(1f))
                Switch(checked = joinOpen, onCheckedChange = { party.setJoinOpen(it) })
            }
            OutlinedButton(onClick = { party.rotateSecret() }) { Text("Nowy kod QR (stary przestaje działać)") }
        }
        item { Text("Goście", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
        if (guests.isEmpty()) item { Text("Nikt jeszcze nie dołączył.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(guests, key = { it.id }) { g ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val online = if (g.id in online) "● " else "○ "
                    Text("$online${g.name}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            g.role == Role.HOST -> "Współhost"
                            g.hostRequested -> "Prosi o uprawnienia hosta"
                            else -> "Gość"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (g.hostRequested) {
                            Button(onClick = { party.approveHost(g.id) }) { Text("Zatwierdź") }
                            OutlinedButton(onClick = { party.rejectHost(g.id) }) { Text("Odrzuć") }
                        }
                        if (g.role == Role.HOST) OutlinedButton(onClick = { party.demote(g.id) }) { Text("Odbierz host") }
                        TextButton(onClick = { party.kick(g.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Usuń") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTab(state: State<Snapshot>, party: PartyController) {
    val savedPlaylistUrl by remember { derivedStateOf { state.value.playlistUrl } }
    val playlistTitle by remember { derivedStateOf { state.value.playlistTitle } }
    val repeat by remember { derivedStateOf { state.value.repeat } }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Meta>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playlistUrl by rememberSaveable(savedPlaylistUrl) { mutableStateOf(savedPlaylistUrl) }
    var shuffle by rememberSaveable { mutableStateOf(true) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { block() } catch (e: Exception) { error = e.message ?: "Błąd" }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Playlista", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(playlistUrl, { playlistUrl = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Link do publicznej playlisty") })
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(shuffle, { shuffle = it }); Text(" Losowo", Modifier.padding(end = 12.dp))
                Switch(repeat, { party.setRepeat(it) }); Text(" Powtarzaj")
            }
            Button(onClick = { run { party.loadPlaylist(playlistUrl, shuffle) } }, enabled = !busy && playlistUrl.isNotBlank()) { Text("Ustaw jako aktualną playlistę") }
            playlistTitle?.let { Text("Wczytano: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            Text("Dodaj utwór", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Szukaj lub wklej link") })
            Button(onClick = {
                run {
                    val id = YouTubeClient.parseVideoId(query)
                    results = if (id != null) listOf(party.yt.meta(id)) else party.yt.search(query)
                }
            }, enabled = !busy && query.isNotBlank()) { Text(if (busy) "Chwila…" else "Szukaj") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(results, key = { it.videoId }) { m ->
            TrackRow(m.title, listOfNotNull(m.channel.ifBlank { null }, m.duration).joinToString(" · ")) {
                Mini("Następna") { party.enqueue(m, "Host", Source.HOST, playNext = true) }
                Mini("Dodaj") { party.enqueue(m, "Host", Source.HOST) }
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
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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
            // A Mix exists only on a watch page: read the "up next" list from the page that is showing.
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
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (listId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(shuffle, { shuffle = it }); Text(" Losowa kolejność")
                    }
                    Button(onClick = { setPlaylist(listId) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Ustaw jako aktualną playlistę")
                    }
                    if (isMix) {
                        Text(
                            "Mix: trafią do kolejki utwory widoczne teraz w mixie (zwykle ok. 25).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (videoId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            work { party.enqueue(party.yt.meta(videoId), "Host", Source.HOST); "Dodano do kolejki" }
                        }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Dodaj do kolejki") }
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
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (!signedIn) {
                    TextButton(
                        onClick = { bridge.browseTo("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F") },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text("Zaloguj się, aby dodać prywatne playlisty") }
                }
            }
        }
    }
}
