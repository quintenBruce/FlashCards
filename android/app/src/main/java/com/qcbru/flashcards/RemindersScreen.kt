@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.qcbru.flashcards

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun RemindersScreen(state: FlashState, model: FlashViewModel, onNotifications: () -> Unit) {
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { NotificationStatus(onNotifications) }
        item {
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add reminder")
            }
        }
        if (state.reminders.isEmpty()) item {
            Text("Choose a time to study. Your reminders will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.reminders.sortedWith(compareBy<StudyReminder> { nextReminderAt(it, now) ?: Long.MAX_VALUE }.thenBy { it.id }), key = { it.id }) { reminder ->
            ReminderCard(
                reminder, state, now,
                onEdit = { editId = reminder.id },
                onToggle = { enabled -> model.saveReminder(reminder.copy(enabled = enabled)) {} },
                onDelete = { deleting = reminder.id }
            )
        }
    }
    if (creating || editId != null) {
        ReminderEditor(
            state.reminders.find { it.id == editId }, state,
            onDismiss = { creating = false; editId = null },
            onSave = { reminder ->
                model.saveReminder(reminder) {
                    creating = false
                    editId = null
                    if (reminder.enabled) onNotifications()
                }
            }
        )
    }
    deleting?.let { id ->
        ConfirmDialog("Delete reminder?", "Future alerts for this reminder will stop.", { deleting = null }) {
            state.reminders.find { it.id == id }?.let(model::deleteReminder)
            deleting = null
        }
    }
}

@Composable
private fun NotificationStatus(onNotifications: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun notificationsAllowed(): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel("study")
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
    var allowed by remember { mutableStateOf(notificationsAllowed()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = notificationsAllowed()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!allowed) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications are off", style = MaterialTheme.typography.titleMedium)
                Text("Enable notifications to receive study reminders.", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onNotifications) { Text("Enable") }
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }) { Text("Settings") }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: StudyReminder, state: FlashState, now: Long,
    onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val timeFormat = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    val expired = reminder.schedule.kind == "once" && (reminder.schedule.at ?: 0) <= now + 1000
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LocalTime.of(reminder.schedule.hour, reminder.schedule.minute).format(timeFormat),
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (!expired) Switch(
                    checked = reminder.enabled, onCheckedChange = onToggle,
                    enabled = state.online && !state.busy,
                    modifier = Modifier.semantics { contentDescription = "Enable ${reminder.title}" }
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Reminder options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() }, enabled = state.online && !state.busy)
                    }
                }
            }
            Text(reminderRepeatLabel(reminder.schedule), style = MaterialTheme.typography.titleSmall)
            if (reminder.title != "Study flashcards") Text(reminder.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val scope = if (reminder.deckIds.isEmpty()) "All decks" else reminder.deckIds.joinToString { id ->
                state.decks.find { it.id == id && it.deletedAt == null && !it.purgeRequested }?.title ?: "Unavailable deck"
            }
            Text(scope, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reminder.filter != "all") Text(reviewLabels.getValue(reminder.filter), style = MaterialTheme.typography.labelLarge)
            val next = nextReminderAt(reminder, now)
            Text(
                when {
                    expired -> "Completed · tap to reschedule"
                    !reminder.enabled -> "Paused"
                    next != null -> "Next · " + Instant.ofEpochMilli(next).atZone(ZoneId.of(reminder.schedule.timeZone))
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d · z"))
                    else -> "No upcoming alert"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val reviewLabels = linkedMapOf(
    "all" to "All cards", "struggling" to "Cards to practice", "starred" to "Starred cards",
    "unrated" to "Unrated cards", "known" to "Known cards"
)

internal fun reminderRepeatLabel(schedule: Schedule): String = when (schedule.kind) {
    "daily" -> "Every day"
    "weekdays" -> schedule.weekdays.distinct().sorted().joinToString(", ") {
        DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    else -> schedule.at?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.of(schedule.timeZone)).format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    } ?: "One time"
}

@Composable
private fun ReminderEditor(original: StudyReminder?, state: FlashState, onDismiss: () -> Unit, onSave: (StudyReminder) -> Unit) {
    val baseJson = rememberSaveable(original?.id) { Codec.encodeToString(original ?: StudyReminder()) }
    val base = remember(baseJson) { Codec.decodeFromString<StudyReminder>(baseJson) }
    val initial = remember(base.id) { ReminderDraft.from(base, System.currentTimeMillis()) }
    var draft by rememberSaveable(base.id, stateSaver = Saver<ReminderDraft, String>(
        save = { Codec.encodeToString(it) }, restore = { Codec.decodeFromString(it) }
    )) { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf("") }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val activeDecks = state.decks.filter { it.deletedAt == null && !it.purgeRequested }
    val timeFormat = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
    FormDialog(if (original == null) "New reminder" else "Edit reminder", onDismiss, dirty = draft != initial) {
        FilledTonalButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Default.Schedule, null)
            Spacer(Modifier.width(12.dp))
            Text(LocalTime.of(draft.hour, draft.minute).format(timeFormat), style = MaterialTheme.typography.headlineMedium)
        }
        Text("Repeat", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("daily" to "Daily", "weekdays" to "Days", "once" to "Once").forEach { (kind, label) ->
                FilterChip(selected = draft.kind == kind, onClick = { draft = draft.copy(kind = kind) }, label = { Text(label) })
            }
        }
        if (draft.kind == "weekdays") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = day in draft.weekdays,
                        onClick = { draft = draft.copy(weekdays = if (day in draft.weekdays) draft.weekdays - day else draft.weekdays + day) },
                        label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }
        }
        if (draft.kind == "once") OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CalendarMonth, null)
            Spacer(Modifier.width(8.dp))
            Text(LocalDate.parse(draft.date).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))
        }
        Text("Decks", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { draft = draft.copy(deckIds = emptyList()) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = draft.deckIds.isEmpty(), onClick = null)
            Text("All decks", Modifier.padding(start = 12.dp))
        }
        activeDecks.forEach { deck ->
            Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                draft = draft.copy(deckIds = if (deck.id in draft.deckIds) draft.deckIds - deck.id else draft.deckIds + deck.id)
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deck.id in draft.deckIds, onCheckedChange = null)
                Text(deck.title, Modifier.padding(start = 12.dp))
            }
        }
        val missingDecks = draft.deckIds.filter { id -> activeDecks.none { it.id == id } }
        if (missingDecks.isNotEmpty()) {
            Text("Some selected decks are unavailable.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { draft = draft.copy(deckIds = draft.deckIds - missingDecks.toSet()) }) { Text("Remove unavailable decks") }
        }
        TextButton(onClick = { more = !more }) { Text(if (more) "Fewer options" else "More options") }
        if (more) {
            OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("Reminder name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, isError = draft.title.trim().length > 200)
            Text("Review", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reviewLabels.forEach { (filter, label) ->
                    FilterChip(selected = draft.filter == filter, onClick = { draft = draft.copy(filter = filter) }, label = { Text(label) })
                }
            }
            Text("Time zone · ${draft.timeZone}", style = MaterialTheme.typography.bodySmall)
            if (draft.timeZone != ZoneId.systemDefault().id) TextButton(onClick = { draft = draft.copy(timeZone = ZoneId.systemDefault().id) }) {
                Text("Use device time zone")
            }
        }
        if (!base.enabled) Text("This reminder is paused. Turn it on from the reminder list after saving.", style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                runCatching { draft.build(base, activeDecks.map { it.id }.toSet(), System.currentTimeMillis()) }
                    .onSuccess(onSave).onFailure { error = it.message ?: "Check your reminder." }
            },
            modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes(),
            enabled = state.online && !state.busy
        ) { Text(if (state.busy) "Saving…" else "Save reminder") }
    }
    if (showTime) {
        val timeState = rememberTimePickerState(draft.hour, draft.minute, DateFormat.is24HourFormat(context))
        AlertDialog(onDismissRequest = { showTime = false }, title = { Text("Study time") },
            text = { Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { TimeInput(timeState) } },
            confirmButton = { TextButton(onClick = {
                draft = draft.copy(hour = timeState.hour, minute = timeState.minute); showTime = false
            }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } })
    }
    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(draft.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                dateState.selectedDateMillis?.let { draft = draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                showDate = false
            }, enabled = dateState.selectedDateMillis != null) { Text("Done") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(dateState) }
    }
}
