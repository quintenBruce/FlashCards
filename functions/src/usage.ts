import { HttpsError } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions';
import { budgetMonth } from '../../shared/src/schedule';
import { cost, reserveAmount } from '../../shared/src/budget';
import { db, root } from './core';

export async function reserve(uid: string, requestId: string, model: string, payload: string, output: number) {
  const month = budgetMonth(Date.now()), amount = reserveAmount(model, payload, output);
  const cap = Number(process.env.AI_MONTHLY_MICRO_USD || 10_000_000);
  if (!Number.isSafeInteger(cap) || cap <= 0) throw new HttpsError('failed-precondition', 'AI budget is not configured');
  const ledger = root(uid).collection('usage').doc(month), reservation = root(uid).collection('reservations').doc(requestId);
  await db.runTransaction(async tx => {
    const [usage, existing] = await Promise.all([tx.get(ledger), tx.get(reservation)]);
    // Never issue the same provider request twice after an interrupted response.
    if (existing.exists) throw new HttpsError('already-exists', 'AI request already started. Refresh the conversation or grade.');
    const total = (usage.get('spent') || 0) + (usage.get('reserved') || 0);
    if (total + amount > cap) throw new HttpsError('resource-exhausted', 'Monthly AI budget reached. Study is still available.');
    tx.set(ledger, { month, spent: usage.get('spent') || 0, reserved: (usage.get('reserved') || 0) + amount, cap, warning: total + amount >= cap * .8, updatedAt: Date.now() });
    tx.create(reservation, { month, model, amount, state: 'reserved', createdAt: Date.now() });
  });
  return { month, amount };
}
export async function settle(uid: string, requestId: string, model: string, input: number, output: number) {
  const reservation = root(uid).collection('reservations').doc(requestId);
  await db.runTransaction(async tx => {
    const entry = await tx.get(reservation);
    if (!entry.exists || entry.get('state') !== 'reserved') return;
    const ledger = root(uid).collection('usage').doc(entry.get('month')), usage = await tx.get(ledger);
    const actual = cost(model, input, output), spent = (usage.get('spent') || 0) + actual;
    const reserved = Math.max(0, (usage.get('reserved') || 0) - entry.get('amount'));
    tx.update(ledger, { spent, reserved, warning: spent + reserved >= usage.get('cap') * .8, updatedAt: Date.now() });
    tx.update(reservation, { state: 'settled', actual, inputTokens: input, outputTokens: output, updatedAt: Date.now() });
  });
  logger.info('ai.usage', { requestId, model, inputTokens: input, outputTokens: output });
}
