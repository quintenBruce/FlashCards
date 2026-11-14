import { getMessaging } from 'firebase-admin/messaging';
import { HttpsError } from 'firebase-functions/v2/https';
import { Schedule } from '../../shared/src/domain';
import { nextOccurrence } from '../../shared/src/schedule';
import { db, root, id, text, integer, strings, object, hash, sameRequest } from './core';

export async function saveReminder(uid: string, data: Record<string, any>) {
  const recordId = id(data.id), requestId = id(data.requestId), raw = object(data.value), s = object(raw.schedule);
  const kind = text(s.kind, 20), timeZone = text(s.timeZone, 80);
  if (!['once','daily','weekdays'].includes(kind)) throw new HttpsError('invalid-argument', 'Invalid recurrence');
  try { new Intl.DateTimeFormat('en', { timeZone }); } catch { throw new HttpsError('invalid-argument', 'Invalid timezone'); }
  const weekdays = Array.isArray(s.weekdays) ? s.weekdays.map((v: unknown) => integer(v, 1, 7)) : [];
  if (kind === 'weekdays' && !weekdays.length) throw new HttpsError('invalid-argument', 'Choose at least one weekday');
  const schedule: Schedule = { kind: kind as any, timeZone, hour: integer(s.hour, 0, 23), minute: integer(s.minute, 0, 59), weekdays, at: kind === 'once' ? integer(s.at, Date.now() + 1000) : null };
  const deckIds = strings(raw.deckIds ?? [], 100).map(id), filter = text(raw.filter || 'all', 20);
  if (!['all','struggling','known','starred','unrated'].includes(filter)) throw new HttpsError('invalid-argument', 'Invalid filter');
  const nextAt = raw.enabled === false ? null : nextOccurrence(schedule, Date.now());
  const ref = root(uid).collection('reminders').doc(recordId), receipt = root(uid).collection('requests').doc(requestId), fingerprint = hash(data);
  return db.runTransaction(async tx => {
    const [prior, current, ...decks] = await Promise.all([tx.get(receipt), tx.get(ref), ...deckIds.map(d => tx.get(root(uid).collection('decks').doc(d)))]);
    sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    if ((current.get('version') || 0) !== integer(data.expectedVersion ?? 0)) throw new HttpsError('failed-precondition', 'Reminder changed');
    if (decks.some(d => !d.exists || d.get('deletedAt') != null)) throw new HttpsError('failed-precondition', 'A deck is unavailable');
    const value = { id: recordId, title: text(raw.title, 200), deckIds, filter, schedule, enabled: raw.enabled !== false,
      nextAt, version: (current.get('version') || 0) + 1, updatedAt: Date.now() };
    tx.set(ref, value); tx.create(receipt, { fingerprint, result: value, createdAt: Date.now() }); return value;
  });
}
export async function reminderAction(uid: string, data: Record<string, any>) {
  const reminderId = id(data.id), requestId = id(data.requestId), action = text(data.action, 20);
  const ref = root(uid).collection('reminders').doc(reminderId), receipt = root(uid).collection('requests').doc(requestId);
  return db.runTransaction(async tx => {
    const [prior, current] = await Promise.all([tx.get(receipt), tx.get(ref)]);
    const fingerprint = hash(data); sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    if (!current.exists) return { removed: true };
    if (action === 'delete') {
      if (current.get('version') !== integer(data.expectedVersion)) throw new HttpsError('failed-precondition', 'Reminder changed');
      tx.delete(ref);
    } else if (action === 'snooze') {
      const scheduledAt = integer(data.scheduledAt);
      const occurrence = await tx.get(root(uid).collection('occurrences').doc(`${reminderId}_${scheduledAt}`));
      if (!occurrence.exists && scheduledAt > Date.now()) throw new HttpsError('failed-precondition', 'Reminder has not fired');
      const snoozeAt = data.snoozeAt == null ? Date.now() + 15 * 60000 : integer(data.snoozeAt, Date.now() - 86400000, Date.now() + 16 * 60000);
      tx.set(root(uid).collection('snoozes').doc(requestId), { reminderId, at: snoozeAt, scheduledAt, sent: false });
    } else throw new HttpsError('invalid-argument', 'Unknown reminder action');
    const result = { action, id: reminderId }; tx.create(receipt, { fingerprint, result, createdAt: Date.now() }); return result;
  });
}
export async function registerDevice(uid: string, data: Record<string, any>) {
  const deviceId = id(data.deviceId);
  if (data.remove === true) { await root(uid).collection('devices').doc(deviceId).delete(); return { removed: true }; }
  await root(uid).collection('devices').doc(deviceId).set({ token: text(data.token, 4096), updatedAt: Date.now() });
  return { registered: true };
}
async function send(uid: string, reminder: Record<string, any>, occurrenceId: string, scheduledAt: number) {
  if ((await db.collection('access').doc(uid).get()).get('enabled') !== true) return { accepted: 0, reason: 'disabled' };
  const devices = await root(uid).collection('devices').get();
  if (!devices.size) return { accepted: 0, reason: 'no_devices' };
  const rows = devices.docs.filter(d => typeof d.get('token') === 'string');
  const result = await getMessaging().sendEachForMulticast({ tokens: rows.map(d => d.get('token')), data: {
    reminderId: reminder.id, occurrenceId, scheduledAt: String(scheduledAt), title: reminder.title, uid,
  }, android: { priority: 'high', ttl: 3600000 } });
  await Promise.all(result.responses.map((r, i) => !r.success && ['messaging/registration-token-not-registered','messaging/invalid-registration-token'].includes(r.error?.code || '') ? rows[i].ref.delete() : Promise.resolve()));
  return { accepted: result.successCount, rejected: result.failureCount, reason: result.successCount ? 'accepted' : 'all_rejected' };
}
export async function dispatchDue() {
  const now = Date.now();
  const due = await db.collectionGroup('reminders').where('enabled','==',true).where('nextAt','<=',now).limit(100).get();
  for (const doc of due.docs) {
    const uid = doc.ref.parent.parent!.id;
    const claim = await db.runTransaction(async tx => {
      const current = await tx.get(doc.ref);
      if (!current.exists || current.get('enabled') !== true || current.get('nextAt') == null || current.get('nextAt') > now) return null;
      const scheduledAt = current.get('nextAt') as number, occurrenceId = `${doc.id}_${scheduledAt}`;
      const ref = root(uid).collection('occurrences').doc(occurrenceId), existing = await tx.get(ref);
      if (existing.get('status') === 'sent') return null;
      if (existing.get('leaseUntil') > now) return null;
      tx.set(ref, { reminderId: doc.id, scheduledAt, status: 'sending', leaseUntil: now + 120000 }, { merge: true });
      return { reminder: current.data()!, scheduledAt, occurrenceId, ref };
    });
    if (!claim) continue;
    try {
      const delivery = await send(uid, claim.reminder, claim.occurrenceId, claim.scheduledAt);
      await db.runTransaction(async tx => {
        const current = await tx.get(doc.ref);
        tx.set(claim.ref, { status: 'sent', sentAt: Date.now(), leaseUntil: 0, ...delivery }, { merge: true });
        if (current.exists && current.get('nextAt') === claim.scheduledAt) {
          const nextAt = nextOccurrence(current.get('schedule'), now);
          tx.update(doc.ref, { nextAt, enabled: nextAt != null, updatedAt: Date.now() });
        }
      });
    } catch { await claim.ref.set({ status: 'retry', leaseUntil: 0 }, { merge: true }); }
  }
  // Only a single personal account is enabled; avoid an unbounded collection-group scan.
  const accounts = await db.collection('access').where('enabled','==',true).limit(10).get();
  for (const account of accounts.docs) {
    const snoozes = await root(account.id).collection('snoozes').where('sent','==',false).get();
    for (const s of snoozes.docs.filter(s => s.get('at') <= now)) {
      const claimed = await db.runTransaction(async tx => {
        const current = await tx.get(s.ref);
        if (current.get('sent') || current.get('leaseUntil') > now) return false;
        tx.update(s.ref, { leaseUntil: now + 120000 }); return true;
      });
      if (!claimed) continue;
      try {
        const r = await root(account.id).collection('reminders').doc(s.get('reminderId')).get();
        if (r.exists) await send(account.id, r.data()!, `snooze_${s.id}`, s.get('scheduledAt'));
        await s.ref.update({ sent: true, leaseUntil: 0 });
      } catch { await s.ref.update({ leaseUntil: 0 }); }
    }
  }
}
