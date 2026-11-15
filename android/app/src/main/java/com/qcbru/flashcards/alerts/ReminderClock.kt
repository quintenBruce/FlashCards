package com.qcbru.flashcards.alerts

import com.qcbru.flashcards.Schedule
import java.time.*

object ReminderClock {
    fun next(schedule: Schedule, after: Long): Long? {
        if (schedule.kind == "once") return schedule.at?.takeIf { it > after }
        val zone = ZoneId.of(schedule.timeZone); val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        for (offset in 0L..8L) {
            val day = start.plusDays(offset)
            if (schedule.kind == "weekdays" && day.dayOfWeek.value !in schedule.weekdays) continue
            val local = day.atTime(schedule.hour,schedule.minute)
            val offsets = zone.rules.getValidOffsets(local)
            val at = if (offsets.isEmpty()) zone.rules.getTransition(local).dateTimeAfter.atZone(zone)
                else ZonedDateTime.ofLocal(local,zone,offsets.first())
            if (at.toInstant().toEpochMilli() > after) return at.toInstant().toEpochMilli()
        }
        return null
    }
}
