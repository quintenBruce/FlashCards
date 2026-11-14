import { HttpsError } from 'firebase-functions/v2/https';
import { root, db, id, text, integer, object, hash, sameRequest } from './core';
import { cardData, deckData } from './content';

export const BACKUP_COLLECTIONS = ['decks','cards','progress','reviews','sessions','reminders','conversations','turns'] as const;
export async function exportPage(uid: string, data: Record<string, any>) {
  const collection = text(data.collection, 30);
  if (!(BACKUP_COLLECTIONS as readonly string[]).includes(collection)) throw new HttpsError('invalid-argument', 'Invalid backup collection');
  let query = root(uid).collection(collection).orderBy('__name__').limit(100);
  if (data.cursor) query = query.startAfter(id(data.cursor));
  const page = await query.get();
  return { rows: page.docs.map(d => ({ ...d.data(), id: d.id })), cursor: page.size === 100 ? page.docs[page.docs.length - 1].id : null };
}
export async function restorePage(uid: string, data: Record<string, any>) {
  const collection = text(data.collection, 30), requestId = id(data.requestId);
  if (!(BACKUP_COLLECTIONS as readonly string[]).includes(collection) || !Array.isArray(data.rows) || data.rows.length > 100 || Buffer.byteLength(JSON.stringify(data.rows)) > 700000) throw new HttpsError('invalid-argument', 'Invalid backup page');
  const rows = data.rows.map((raw: unknown) => {
    const r = object(raw), recordId = id(r.id);
    if (collection === 'decks') return { ...deckData(r), id: recordId, version: integer(r.version,1), deletedAt: r.deletedAt == null ? null : integer(r.deletedAt), updatedAt: Date.now() };
    if (collection === 'cards') return { ...cardData(r), id: recordId, version: integer(r.version,1), contentVersion: integer(r.contentVersion || r.version,1), deletedAt: r.deletedAt == null ? null : integer(r.deletedAt), updatedAt: Date.now() };
    if (collection === 'reminders') return { ...r, id: recordId, enabled: false, nextAt: null, updatedAt: Date.now() };
    return { ...r, id: recordId };
  });
  const refs = rows.map((r: any) => root(uid).collection(collection).doc(r.id));
  if (new Set(refs.map(r => r.path)).size !== refs.length) throw new HttpsError('invalid-argument', 'Duplicate backup IDs');
  const receipt = root(uid).collection('requests').doc(requestId), fingerprint = hash(data);
  return db.runTransaction(async tx => {
    const [prior, ...existing] = await Promise.all([tx.get(receipt), ...refs.map(ref => tx.get(ref))]);
    sameRequest(prior, fingerprint); if (prior.exists) return prior.get('result');
    if (existing.some(r => r.exists)) throw new HttpsError('already-exists', 'Backup restore never overwrites existing records');
    rows.forEach((row, i) => tx.create(refs[i], row));
    const result = { restored: rows.length }; tx.create(receipt, { fingerprint, result, createdAt: Date.now() }); return result;
  });
}
