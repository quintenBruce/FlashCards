import { Temporal } from '@js-temporal/polyfill';
import { Schedule } from './domain';

export function nextOccurrence(rule: Schedule, after: number): number | null {
  if (rule.kind === 'once') return rule.at != null && rule.at > after ? rule.at : null;
  const date = Temporal.Instant.fromEpochMilliseconds(after).toZonedDateTimeISO(rule.timeZone).toPlainDate();
  for (let offset = 0; offset <= 8; offset++) {
    const day = date.add({ days: offset });
    if (rule.kind === 'weekdays' && !rule.weekdays.includes(day.dayOfWeek)) continue;
    const local = day.toPlainDateTime({ hour: rule.hour, minute: rule.minute });
    const fields = { timeZone: rule.timeZone, year: day.year, month: day.month, day: day.day, hour: rule.hour, minute: rule.minute };
    let at = Temporal.ZonedDateTime.from(fields, { disambiguation: 'earlier' });
    if (!at.toPlainDateTime().equals(local)) {
      // Gap: choose the first valid minute, not an arbitrary one-hour offset.
      for (let minute = 1; minute <= 180; minute++) {
        const candidate = local.add({ minutes: minute });
        try { at = candidate.toZonedDateTime(rule.timeZone, { disambiguation: 'reject' }); break; } catch { /* gap */ }
      }
    }
    if (at.epochMilliseconds > after) return at.epochMilliseconds;
  }
  return null;
}
export function budgetMonth(now: number): string {
  return Temporal.Instant.fromEpochMilliseconds(now).toZonedDateTimeISO('America/Chicago').toPlainDate().toString().slice(0, 7);
}
