import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { onCall, CallableRequest, HttpsError } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions';
import { createHash, randomUUID } from 'node:crypto';

initializeApp();
export const db = getFirestore();
export const auth = getAuth();
export const region = process.env.FUNCTIONS_REGION || 'us-south1';
export const root = (uid: string) => db.collection('users').doc(uid);
export const hash = (data: unknown) => createHash('sha256').update(JSON.stringify(data)).digest('hex');
export function object(data: unknown): Record<string, any> {
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new HttpsError('invalid-argument', 'Expected an object');
  return data as Record<string, any>;
}
export function text(data: unknown, max = 500, allowEmpty = false): string {
  if (typeof data !== 'string' || data.length > max || (!allowEmpty && !data.trim())) throw new HttpsError('invalid-argument', 'Invalid text');
  return data.trim();
}
export function id(data: unknown): string {
  const value = text(data, 100);
  if (!/^[a-zA-Z0-9_-]+$/.test(value)) throw new HttpsError('invalid-argument', 'Invalid ID');
  return value;
}
export function integer(data: unknown, min = 0, max = Number.MAX_SAFE_INTEGER): number {
  if (!Number.isSafeInteger(data) || (data as number) < min || (data as number) > max) throw new HttpsError('invalid-argument', 'Invalid number');
  return data as number;
}
export function strings(data: unknown, max = 100): string[] {
  if (!Array.isArray(data) || data.length > max) throw new HttpsError('invalid-argument', 'Invalid list');
  return data.map(v => text(v, 1000));
}
export async function requireOwner(request: CallableRequest): Promise<string> {
  const user = request.auth;
  if (!user || user.token.email_verified !== true || user.token.firebase?.sign_in_provider !== 'google.com') throw new HttpsError('unauthenticated', 'Sign in with your verified Google account');
  const access = await db.collection('access').doc(user.uid).get();
  if (access.get('enabled') !== true) throw new HttpsError('permission-denied', 'This account is not enabled');
  return user.uid;
}
export function callable(name: string, handler: (uid: string, data: Record<string, any>) => Promise<unknown>, secrets: string[] = []) {
  return onCall({ region, enforceAppCheck: true, maxInstances: 2, minInstances: 0, timeoutSeconds: 540, secrets }, async request => {
    const started = Date.now();
    const operationId = randomUUID();
    try {
      const uid = await requireOwner(request);
      const result = await handler(uid, object(request.data));
      logger.info('operation.succeeded', { operationId, operation: name, elapsedMs: Date.now() - started });
      return result;
    } catch (error) {
      logger.warn('operation.failed', { operationId, operation: name, code: error instanceof HttpsError ? error.code : 'internal', elapsedMs: Date.now() - started });
      if (error instanceof HttpsError) throw error;
      throw new HttpsError('internal', 'The operation could not be completed. Retry safely.');
    }
  });
}
export function version(snapshot: FirebaseFirestore.DocumentSnapshot, expected: unknown) {
  if (!snapshot.exists) throw new HttpsError('not-found', 'Record no longer exists');
  if (snapshot.get('version') !== integer(expected)) throw new HttpsError('failed-precondition', 'Content changed. Refresh and review again.');
}
export function sameRequest(snapshot: FirebaseFirestore.DocumentSnapshot, fingerprint: string) {
  if (snapshot.exists && snapshot.get('fingerprint') !== fingerprint) throw new HttpsError('already-exists', 'Request ID already belongs to a different operation');
}
