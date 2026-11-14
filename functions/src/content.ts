import { HttpsError } from 'firebase-functions/v2/https';
import { Card, Deck } from '../../shared/src/domain';
import { db, root, id, text, strings, integer, object, hash, version, sameRequest } from './core';

export function deckData(raw: unknown): Omit<Deck, 'id' | 'version' | 'updatedAt' | 'deletedAt'> {
  const d = object(raw);
  const termLanguage = text(d.termLanguage, 35), definitionLanguage = text(d.definitionLanguage, 35);
  for (const language of [termLanguage, definitionLanguage]) {
    try { new Intl.Locale(language); } catch { throw new HttpsError('invalid-argument', 'Invalid language'); }
  }
  return { title: text(d.title, 200), description: text(d.description ?? '', 2000, true), termLanguage, definitionLanguage,
    spanishVariety: text(d.spanishVariety ?? 'es-419', 35), archived: d.archived === true };
}
export function cardData(raw: unknown): Omit<Card, 'id' | 'version' | 'updatedAt' | 'deletedAt'> {
  const c = object(raw);
  return { deckId: id(c.deckId), term: text(c.term, 2000), definition: text(c.definition, 4000),
    explanation: text(c.explanation ?? '', 4000, true), example: text(c.example ?? '', 2000, true),
    termAlternatives: strings(c.termAlternatives ?? [], 20), definitionAlternatives: strings(c.definitionAlternatives ?? [], 20),
    starred: c.starred === true, order: integer(c.order ?? 0) };
}
export async function mutate(uid: string, data: Record<string, any>) {
  const requestId = id(data.requestId), entity = text(data.entity, 20), action = text(data.action, 20);
  if (!['decks', 'cards', 'reminders', 'conversations'].includes(entity)) throw new HttpsError('invalid-argument', 'Unknown entity');
  if (!['save', 'trash', 'restore', 'purge'].includes(action)) throw new HttpsError('invalid-argument', 'Unknown action');
  if (entity === 'reminders' || entity === 'conversations') throw new HttpsError('invalid-argument', 'Use the dedicated endpoint');
  const recordId = id(data.id), ref = root(uid).collection(entity).doc(recordId);
  const receipt = root(uid).collection('requests').doc(requestId), fingerprint = hash(data);
  const clean = action === 'save' ? (entity === 'decks' ? deckData(data.value) : cardData(data.value)) : null;
  return db.runTransaction(async tx => {
    const [prior, current] = await Promise.all([tx.get(receipt), tx.get(ref)]);
    sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    if (current.exists) version(current, data.expectedVersion);
    else if (action !== 'save' || integer(data.expectedVersion ?? 0) !== 0) throw new HttpsError('not-found', 'Record no longer exists');
    const parentRef = entity === 'cards' ? root(uid).collection('decks').doc(clean ? (clean as any).deckId : current.get('deckId')) : null;
    const parent = parentRef ? await tx.get(parentRef) : null;
    if (parent && (!parent.exists || parent.get('deletedAt') != null)) throw new HttpsError('failed-precondition', 'Deck is unavailable');
    if (entity === 'cards' && clean && current.exists && current.get('deckId') !== (clean as any).deckId) throw new HttpsError('invalid-argument', 'Cards cannot change decks');
    if (action === 'restore') {
      if (current.get('deletedAt') == null || current.get('deletedAt') < Date.now() - 30 * 86400000) throw new HttpsError('failed-precondition', 'Restore window expired');
    }
    if (action === 'save' && current.get('deletedAt') != null) throw new HttpsError('failed-precondition', 'Restore this item first');
    const now = Date.now(), nextVersion = (current.get('version') || 0) + 1;
    const result = { id: recordId, version: nextVersion };
    if (action === 'purge') {
      if (current.get('deletedAt') == null) throw new HttpsError('failed-precondition', 'Move to trash before permanent deletion');
      // Tombstone is hidden immediately; scheduled cleanup safely removes children.
      tx.update(ref, { purgeRequested: true, updatedAt: now, version: nextVersion });
    } else if (action === 'save') tx.set(ref, { ...clean, id: recordId, version: nextVersion, deletedAt: null, updatedAt: now });
    else tx.update(ref, { deletedAt: action === 'trash' ? now : null, version: nextVersion, updatedAt: now });
    if (parentRef && parent) tx.update(parentRef, { version: parent.get('version') + 1, updatedAt: now });
    tx.create(receipt, { fingerprint, result, createdAt: now });
    return result;
  });
}
export async function importCards(uid: string, data: Record<string, any>) {
  const requestId = id(data.requestId), deckId = id(data.deckId);
  if (!Array.isArray(data.cards) || data.cards.length < 1 || data.cards.length > 100) throw new HttpsError('invalid-argument', 'Import 1–100 cards per confirmed batch');
  const cards = data.cards.map((c: unknown) => { const item = object(c); return { ...cardData({ ...item, deckId }), id: id(item.id) }; });
  if (new Set(cards.map(c => c.id)).size !== cards.length) throw new HttpsError('invalid-argument', 'Duplicate IDs');
  const parent = root(uid).collection('decks').doc(deckId), receipt = root(uid).collection('requests').doc(requestId), fingerprint = hash(data);
  return db.runTransaction(async tx => {
    const [prior, deck, ...existing] = await Promise.all([tx.get(receipt), tx.get(parent), ...cards.map(c => tx.get(root(uid).collection('cards').doc(c.id)))]);
    sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    version(deck, data.expectedVersion);
    if (deck.get('deletedAt') != null || existing.some(c => c.exists)) throw new HttpsError('failed-precondition', 'Import targets changed');
    for (const card of cards) tx.create(root(uid).collection('cards').doc(card.id), { ...card, version: 1, deletedAt: null, updatedAt: Date.now() });
    tx.update(parent, { version: deck.get('version') + 1, updatedAt: Date.now() });
    const result = { imported: cards.length, version: deck.get('version') + 1 };
    tx.create(receipt, { fingerprint, result, createdAt: Date.now() }); return result;
  });
}
