@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.qcbru.flashcards

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val studyFilters = linkedMapOf(
    "all" to "All cards", "struggling" to "Needs practice", "unrated" to "Unrated",
    "starred" to "Starred", "known" to "Known"
)
private val studyModes = linkedMapOf(
    "practice" to "Flip and rate", "browse" to "Browse", "typed" to "Written answers",
    "multiple" to "Multiple choice", "test" to "Test"
)

@Composable
internal fun StudyScreen(
    state: FlashState,
    onStart: (Set<String>, StudyOptions) -> Unit,
    onOpen: (String) -> Unit,
    onDecks: () -> Unit
) {
    var allDecks by rememberSaveable(state.uid) { mutableStateOf(true) }
    var selected by rememberSaveable(state.uid) { mutableStateOf(emptyList<String>()) }
    var chooseDecks by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var mode by rememberSaveable { mutableStateOf("practice") }
    var direction by rememberSaveable { mutableStateOf("term") }
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var count by rememberSaveable { mutableStateOf("20") }
    var more by rememberSaveable { mutableStateOf(false) }
    var fullHistory by rememberSaveable { mutableStateOf(false) }
    val activeDecks = state.decks.filter { it.deletedAt == null && !it.purgeRequested }
    val continuing = activeStudies(state.sessions).filter { session -> activeDecks.any { it.id in session.studyDeckIds() } }
    val available = availableStudyDecks(state.decks, state.sessions, state.online)
    val scope = runCatching { newStudyScope(state.decks, state.sessions, if (allDecks) null else selected.toSet(), state.online) }
    val options = StudyOptions(filter, direction, shuffle, mode, count.toIntOrNull() ?: 0)
    val validOptions = runCatching { options.validate() }.isSuccess
    val matchingCount = scope.getOrNull()?.let { matchingStudyCount(it, state.cards, state.progress, options) }
    val history = state.sessions.filter { (it.finished || it.submitted) && it.supersededBy == null && it.items.isNotEmpty() }
        .sortedWith(compareByDescending<StudySession> { it.updatedAt }.thenByDescending { it.id })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (continuing.isNotEmpty()) {
            item { Text("Continue", style = MaterialTheme.typography.titleLarge) }
            items(continuing, key = { "continue_${it.id}" }) { session ->
                ContinueStudyCard(session, !state.busy) { onOpen(session.id) }
            }
        }
        if (state.loading && activeDecks.isEmpty()) {
            item { LoadingIndicator() }
        } else if (activeDecks.isEmpty()) {
            item {
                Text("Add a deck to start studying.", style = MaterialTheme.typography.bodyLarge)
                Button(onDecks, modifier = Modifier.padding(top = 16.dp), shapes = ButtonDefaults.shapes()) { Text("Go to decks") }
            }
        } else if (available.isEmpty()) {
            item { Text(
                if (continuing.isNotEmpty()) "Continue a session above to keep studying."
                else "Connect to load a deck before studying offline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) }
        } else {
            item {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("New session", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { chooseDecks = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Style, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (allDecks) "All available decks" else selected.joinToString { id -> state.decks.find { it.id == id }?.title ?: "Unavailable deck" }.ifEmpty { "Choose decks" },
                                modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ExpandMore, null)
                        }
                        Text("Cards", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            studyFilters.forEach { (value, label) ->
                                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                            }
                        }
                        if (scope.isFailure) Text(scope.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                        else Text(
                            when (matchingCount) {
                                null -> "Up to ${options.count.coerceAtLeast(1)} cards · ${studyModes.getValue(mode)}"
                                0 -> "No cards match. Change your decks or card filter."
                                else -> "${minOf(matchingCount, options.count.coerceAtLeast(1))} ${if (direction == "both") "card sides" else "cards"} · ${studyModes.getValue(mode)}"
                            }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                        )
                        if (!state.online) Text("Using decks saved on this device.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { more = !more }) { Text(if (more) "Fewer options" else "Session options") }
                        if (more) {
                            StudyOptionMenu("Mode", studyModes, mode) { mode = it }
                            StudyOptionMenu("Start with", linkedMapOf("term" to "Front", "definition" to "Back", "both" to "Both directions"), direction) { direction = it }
                            OutlinedTextField(
                                count, { count = it }, label = { Text("Cards (1–100)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = count.toIntOrNull() !in 1..100,
                                supportingText = { if (count.toIntOrNull() !in 1..100) Text("Choose between 1 and 100 cards.") }
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Shuffle", Modifier.weight(1f))
                                Switch(shuffle, { shuffle = it })
                            }
                        }
                        Button(
                            onClick = { scope.getOrNull()?.let { onStart(it.map { deck -> deck.id }.toSet(), options) } },
                            enabled = !state.busy && scope.isSuccess && validOptions && matchingCount != 0,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shapes = ButtonDefaults.shapesFor(56.dp)
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.busy) "Opening…" else if (mode == "practice") "Start practice" else "Start ${studyModes.getValue(mode).lowercase()}")
                        }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item { Text("Recent sessions", style = MaterialTheme.typography.titleLarge) }
            items(if (fullHistory) history else history.take(5), key = { "history_${it.id}" }) { session ->
                CompletedStudyRow(session, !state.busy) { onOpen(session.id) }
            }
            if (history.size > 5) item {
                TextButton(onClick = { fullHistory = !fullHistory }) { Text(if (fullHistory) "Show recent" else "Show all sessions") }
            }
        }
    }
    if (chooseDecks) {
        ModalBottomSheet(onDismissRequest = { chooseDecks = false }) {
            var search by rememberSaveable { mutableStateOf("") }
            val occupied = activeStudies(state.sessions).flatMap { it.studyDeckIds() }.toSet()
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 16.dp)) {
                SearchInput(search, { search = it }, "Find a deck")
                LazyColumn(Modifier.weight(1f, fill = false).padding(top = 16.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(allDecks, role = Role.RadioButton, onClick = { allDecks = true; selected = emptyList() }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(allDecks, onClick = null)
                            Text("All available decks", Modifier.padding(start = 12.dp))
                        }
                    }
                    items(activeDecks.filter { it.title.contains(search.trim(), true) }, key = { it.id }) { deck ->
                        val selectable = available.any { it.id == deck.id }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(!allDecks && deck.id in selected, enabled = selectable, role = Role.Checkbox) { checked ->
                                allDecks = false
                                selected = if (checked) selected + deck.id else selected - deck.id
                            }, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(!allDecks && deck.id in selected, onCheckedChange = null, enabled = selectable)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(deck.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!selectable) Text(if (deck.id in occupied) "Session in progress" else "Connect to load", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Button(onClick = { chooseDecks = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), shapes = ButtonDefaults.shapes()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun StudyOptionMenu(label: String, values: Map<String, String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${values.getValue(selected)}", Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
private fun ContinueStudyCard(session: StudySession, enabled: Boolean, onOpen: () -> Unit) {
    val progress = studySessionProgress(session)
    Card(shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${studyModes[session.mode] ?: session.mode} · ${progress.completed} of ${progress.total} ${if (session.mode in listOf("practice", "browse")) "complete" else "answered"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            if (session.syncConflict) Text("Saved on this device. Cloud changes need review.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onOpen, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Resume") }
        }
    }
}

@Composable
private fun CompletedStudyRow(session: StudySession, enabled: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val date = Instant.ofEpochMilli(session.updatedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "MMM d · HH:mm" else "MMM d · h:mm a"))
    val grades = session.items.mapNotNull { session.grades[it.key] }
    val pending = grades.count { it.points == null }
    OutlinedCard(onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${studyModes[session.mode] ?: session.mode} · $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (session.mode in listOf("practice", "browse")) "${session.items.size} cards completed"
                else "${"%.1f".format(grades.mapNotNull { it.points }.sum())} / ${session.items.size} points${if (pending > 0) " · $pending awaiting review" else ""}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (session.syncConflict) Text("Saved on this device", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
