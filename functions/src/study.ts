import { HttpsError } from 'firebase-functions/v2/https';
import { db, root, id, text, integer, object, hash, sameRequest } from './core';

export async function syncReview(uid: string, data: Record<string, any>) {
  const eventId = id(data.id), cardId = id(data.cardId), sessionId = id(data.sessionId);
  const direction = text(data.direction, 20), confidence = text(data.confidence, 20);
  if (!['term', 'definition'].includes(direction) || !['low', 'medium', 'high'].includes(confidence)) throw new HttpsError('invalid-argument', 'Invalid rating');
  const cardVersion = integer(data.cardVersion, 1), expectedVersion = integer(data.expectedVersion);
  const event = root(uid).collection('reviews').doc(eventId), cardRef = root(uid).collection('cards').doc(cardId);
  const progress = root(uid).collection('progress').doc(`${cardId}_${direction}`);
  const fingerprint = hash(data);
  return db.runTransaction(async tx => {
    const [prior, card, current] = await Promise.all([tx.get(event), tx.get(cardRef), tx.get(progress)]);
    sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    const parent = card.exists ? await tx.get(root(uid).collection('decks').doc(card.get('deckId'))) : null;
    const unchanged = card.exists && card.get('deletedAt') == null && (card.get('contentVersion') || card.get('version')) === cardVersion && parent?.exists && parent.get('deletedAt') == null;
    const undoId = data.undoEventId ? id(data.undoEventId) : null;
    const original = undoId ? await tx.get(root(uid).collection('reviews').doc(undoId)) : null;
    let applied = !!unchanged && (current.get('version') || 0) === expectedVersion;
    if (undoId) applied = applied && original?.get('cardId') === cardId && original?.get('direction') === direction && current.get('eventId') === undoId;
    const newVersion = (current.get('version') || 0) + (applied ? 1 : 0);
    const result = { applied, progress: applied ? { id: progress.id, cardId, direction, confidence,
      cardVersion, version: newVersion, eventId, updatedAt: Date.now() } : current.data() ?? null };
    if (applied) {
      if (undoId && original?.get('previous') == null) tx.delete(progress);
      else tx.set(progress, undoId ? { ...original!.get('previous'), version: newVersion, eventId, updatedAt: Date.now() } : result.progress!);
    }
    const actual = undoId && applied ? (original?.get('previous') ? { ...original.get('previous'), version: newVersion, eventId, updatedAt: Date.now() } : null) : result.progress;
    const response = { applied, progress: actual };
    tx.create(event, { id: eventId, cardId, sessionId, direction, confidence, cardVersion, expectedVersion, undoEventId: undoId,
      occurredAt: integer(data.occurredAt), receivedAt: Date.now(), previous: current.data() ?? null, fingerprint, result: response });
    return response;
  });
}
export async function saveSession(uid: string, data: Record<string, any>) {
  const sessionId = id(data.id), requestId = id(data.requestId), value = object(data.session);
  // Immutable snapshots are private user data; authoritative confidence is changed only by syncReview.
  if (Buffer.byteLength(JSON.stringify(value)) > 700_000) throw new HttpsError('invalid-argument', 'Session too large');
  const ref = root(uid).collection('sessions').doc(sessionId), receipt = root(uid).collection('requests').doc(requestId), fingerprint = hash(data);
  return db.runTransaction(async tx => {
    const [prior, current] = await Promise.all([tx.get(receipt), tx.get(ref)]);
    sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    if ((current.get('version') || 0) !== integer(data.expectedVersion ?? 0)) throw new HttpsError('failed-precondition', 'Session changed on another device');
    const result = { id: sessionId, version: (current.get('version') || 0) + 1 };
    tx.set(ref, { id: sessionId, value, version: result.version, updatedAt: Date.now() });
    tx.create(receipt, { fingerprint, result, createdAt: Date.now() }); return result;
  });
}
