package pl.mleczki.partyqueue

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** The button at the top of the queue that opens the search. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.5.dp, Brand.horizontal(), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text("Szukaj piosenki do dodania", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Full-screen search. Every result has two icon buttons: an arrow pointing to the top of the list ("play next")
 * and one pointing to the bottom ("add to the end"). The dialog stays open so several songs can be added in a row.
 */
@Composable
fun SearchDialog(party: PartyController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Meta>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // videoId -> "next" or "end", to show what was done with a result.
    val added = remember { mutableStateListOf<Pair<String, String>>() }

    fun search() {
        val q = query.trim()
        if (q.isEmpty() || busy) return
        focusManager.clearFocus()
        scope.launch {
            busy = true
            message = null
            try {
                val id = YouTubeClient.parseVideoId(q)
                results = if (id != null) listOf(party.yt.meta(id)) else party.yt.search(q)
                if (results.isEmpty()) message = "Nic nie znaleziono"
            } catch (e: Exception) {
                message = e.message ?: "Nie udało się wyszukać"
            }
            busy = false
        }
    }

    fun add(m: Meta, next: Boolean) {
        party.enqueue(m, "Host", Source.HOST, playNext = next, atEnd = !next)
        added.removeAll { it.first == m.videoId }
        added += m.videoId to (if (next) "next" else "end")
        Toast.makeText(ctx, if (next) "Zagra jako następna: ${m.title}" else "Na końcu kolejki: ${m.title}", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { CloseGlyph(MaterialTheme.colorScheme.onBackground) }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Tytuł, wykonawca albo link") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        shape = RoundedCornerShape(50),
                    )
                    Spacer(Modifier.width(8.dp))
                    GradientButton("Szukaj", { search() }, enabled = !busy && query.isNotBlank())
                }
                // What the two icons mean.
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    QueuePlaceGlyph(MaterialTheme.colorScheme.primary, toEnd = false)
                    Text("jako następna", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QueuePlaceGlyph(MaterialTheme.colorScheme.primary, toEnd = true)
                    Text("na koniec kolejki", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) Text("Szukam…", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
                message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.videoId }) { m ->
                        val done = added.lastOrNull { it.first == m.videoId }?.second
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = "https://i.ytimg.com/vi/${m.videoId}/mqdefault.jpg",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 72.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        listOfNotNull(m.channel.ifBlank { null }, m.duration, when (done) { "next" -> "✓ jako następna"; "end" -> "✓ na końcu"; else -> null }).joinToString(" · "),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (done != null) Brand.Violet.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (done != null) FontWeight.Bold else null,
                                    )
                                }
                                PlaceButton(toEnd = false, chosen = done == "next") { add(m, next = true) }
                                PlaceButton(toEnd = true, chosen = done == "end") { add(m, next = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceButton(toEnd: Boolean, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (chosen) Brand.horizontal() else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { QueuePlaceGlyph(if (chosen) Color.White else MaterialTheme.colorScheme.primary, toEnd = toEnd, size = 26.dp) }
}
