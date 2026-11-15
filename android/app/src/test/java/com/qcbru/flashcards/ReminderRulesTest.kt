package com.qcbru.flashcards

import com.qcbru.flashcards.alerts.ReminderClock
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReminderRulesTest {
    private val now = Instant.parse("2026-11-15T18:00:00Z").toEpochMilli()
    private val base = StudyReminder(id = "review", schedule = Schedule(timeZone = "America/Chicago"), version = 3)
    private fun draft(reminder: StudyReminder = base) = ReminderDraft.from(reminder, now)

    @Test fun dailyReminderKeepsIdentityAndUsesAllDecks() {
        val result = draft().copy(title = "  Evening review  ").build(base, emptySet(), now)
        assertEquals("review", result.id)
        assertEquals(3, result.version)
        assertEquals("Evening review", result.title)
        assertTrue(result.deckIds.isEmpty())
        assertEquals(Instant.parse("2026-11-16T01:00:00Z").toEpochMilli(), nextReminderAt(result, now))
    }

    @Test fun blankNameUsesDefaultAndPausedEditsStayPaused() {
        val paused = base.copy(enabled = false)
        val result = draft(paused).copy(title = " ").build(paused, emptySet(), now)
        assertEquals("Study flashcards", result.title)
        assertFalse(result.enabled)
        assertNull(nextReminderAt(result, now))
    }

    @Test fun changingRepeatClearsStaleDateDaysAndNextOccurrence() {
        val existing = base.copy(nextAt = now + 5000)
        val result = draft(existing).copy(weekdays = listOf(1, 2), date = "2026-11-14").build(existing, emptySet(), now)
        assertNull(result.schedule.at)
        assertTrue(result.schedule.weekdays.isEmpty())
        assertNull(result.nextAt)
    }

    @Test fun selectedDaysAreDeduplicatedAndSkipUnselectedDays() {
        val result = draft().copy(kind = "weekdays", weekdays = listOf(5, 1, 1)).build(base, emptySet(), now)
        assertEquals(listOf(1, 5), result.schedule.weekdays)
        assertEquals(Instant.parse("2026-11-17T01:00:00Z").toEpochMilli(), nextReminderAt(result, now))
    }

    @Test fun selectedDaysRequireAtLeastOneValidDay() {
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(kind = "weekdays").build(base, emptySet(), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(kind = "weekdays", weekdays = listOf(8)).build(base, emptySet(), now)
        }
    }

    @Test fun onceUsesTheSelectedDateAndOriginalTimeZone() {
        val result = draft().copy(kind = "once", date = "2026-11-16", hour = 9, minute = 30).build(base, emptySet(), now)
        assertEquals(Instant.parse("2026-11-16T15:30:00Z").toEpochMilli(), result.schedule.at)
        assertEquals(result.schedule.at, nextReminderAt(result, now))
        assertNull(nextReminderAt(result, result.schedule.at!!))
    }

    @Test fun pastOrNearImmediateOneTimeReminderIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(kind = "once", date = "2026-11-14").build(base, emptySet(), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(kind = "once", date = "2026-11-15", hour = 12, minute = 0).build(base, emptySet(), now)
        }
    }

    @Test fun selectedDecksCannotSilentlyBecomeAllDecksWhenRemoved() {
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(deckIds = listOf("deleted")).build(base, setOf("travel"), now)
        }
        val result = draft().copy(deckIds = listOf("travel", "travel")).build(base, setOf("travel"), now)
        assertEquals(listOf("travel"), result.deckIds)
    }

    @Test fun titleAndDeckLimitsAreValidatedBeforeSending() {
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(title = "x".repeat(201)).build(base, emptySet(), now)
        }
        val ids = (1..101).map(Int::toString)
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(deckIds = ids).build(base, ids.toSet(), now)
        }
    }

    @Test fun unknownRepeatAndFilterAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(kind = "monthly").build(base, emptySet(), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            draft().copy(filter = "missing").build(base, emptySet(), now)
        }
    }

    @Test fun pausedAndDeletedRemindersCannotDeliverOrSnooze() {
        assertFalse(canDeliverReminder(base.copy(enabled = false)))
        assertFalse(canDeliverReminder(null))
        assertFalse(canDeliverReminder(base.copy(enabled = false), base.version))
        assertTrue(canDeliverReminder(base))
    }

    @Test fun snoozesFromEarlierVersionsAreInvalidatedAndLegacySnoozesStillWork() {
        assertFalse(canDeliverReminder(base, base.version - 1))
        assertTrue(canDeliverReminder(base, base.version))
        assertTrue(canDeliverReminder(base, null))
    }

    @Test fun upcomingAlertUsesServerSnoozeThenRecalculatesStaleOccurrences() {
        assertEquals(now + 300_000, nextReminderAt(base.copy(nextAt = now + 300_000), now))
        assertEquals(nextReminderAt(base, now), nextReminderAt(base.copy(nextAt = now - 1000), now))
    }

    @Test fun dailySpringGapAndAutumnOverlapRetainLocalSchedule() {
        val gap = Schedule(timeZone = "America/Chicago", hour = 2, minute = 30)
        assertEquals(Instant.parse("2027-03-14T08:00:00Z").toEpochMilli(),
            ReminderClock.next(gap, Instant.parse("2027-03-14T06:00:00Z").toEpochMilli()))
        val overlap = gap.copy(hour = 1, minute = 30)
        assertEquals(Instant.parse("2026-11-02T07:30:00Z").toEpochMilli(),
            ReminderClock.next(overlap, Instant.parse("2026-11-01T06:31:00Z").toEpochMilli()))
    }
}
