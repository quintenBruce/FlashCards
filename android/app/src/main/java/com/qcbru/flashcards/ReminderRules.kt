package com.qcbru.flashcards

import com.qcbru.flashcards.alerts.ReminderClock
import kotlinx.serialization.Serializable
import java.time.*

@Serializable
internal data class ReminderDraft(
    val title: String,
    val kind: String,
    val timeZone: String,
    val hour: Int,
    val minute: Int,
    val date: String,
    val weekdays: List<Int>,
    val deckIds: List<String>,
    val filter: String
) {
    fun build(base: StudyReminder, availableDeckIds: Set<String>, now: Long): StudyReminder {
        require(title.trim().length <= 200) { "Keep the name to 200 characters or fewer." }
        require(kind in listOf("once", "daily", "weekdays")) { "Choose a repeat option." }
        require(filter in listOf("all", "struggling", "known", "starred", "unrated")) { "Choose which cards to review." }
        require(deckIds.size <= 100) { "Choose up to 100 decks." }
        require(deckIds.all { it in availableDeckIds }) { "A selected deck is unavailable. Update your selection." }
        val zone = ZoneId.of(timeZone)
        val time = LocalTime.of(hour, minute)
        val days = if (kind == "weekdays") weekdays.distinct().sorted() else emptyList()
        require(kind != "weekdays" || days.isNotEmpty()) { "Choose at least one day." }
        require(days.all { it in 1..7 }) { "Choose valid weekdays." }
        val at = if (kind == "once") LocalDate.parse(date).atTime(time).atZone(zone).toInstant().toEpochMilli() else null
        require(at == null || at > now + 1000) { "Choose a future date and time." }
        return base.copy(
            title = title.trim().ifEmpty { "Study flashcards" },
            schedule = Schedule(kind, timeZone, hour, minute, days, at),
            deckIds = deckIds.distinct(),
            filter = filter,
            nextAt = null
        )
    }

    companion object {
        fun from(reminder: StudyReminder, now: Long): ReminderDraft {
            val s = reminder.schedule
            val date = Instant.ofEpochMilli(s.at ?: now).atZone(ZoneId.of(s.timeZone)).toLocalDate().toString()
            return ReminderDraft(reminder.title, s.kind, s.timeZone, s.hour, s.minute, date, s.weekdays, reminder.deckIds, reminder.filter)
        }
    }
}

internal fun nextReminderAt(reminder: StudyReminder, now: Long): Long? {
    if (!reminder.enabled) return null
    return reminder.nextAt?.takeIf { it > now } ?: ReminderClock.next(reminder.schedule, now)
}

internal fun canDeliverReminder(reminder: StudyReminder?, snoozeVersion: Int? = null): Boolean =
    reminder != null && reminder.enabled && (snoozeVersion == null || snoozeVersion == reminder.version)
