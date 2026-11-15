import OpenAI from 'openai';
import { HttpsError } from 'firebase-functions/v2/https';

// Do not expose provider bodies: they can contain prompts, answers, or credentials.
export function publicError(error: unknown): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof OpenAI.APIConnectionTimeoutError) return new HttpsError('deadline-exceeded', 'The AI provider timed out. Check the request status before sending again.');
  if (error instanceof OpenAI.APIConnectionError) return new HttpsError('unavailable', 'Could not reach the AI provider. Check the request status before sending again.');
  if (error instanceof OpenAI.APIError) {
    if (error.status === 401 || error.status === 403) return new HttpsError('failed-precondition', 'The AI provider rejected the server credentials or model access. Check OPENAI_API_KEY and model permissions.');
    if (error.status === 404) return new HttpsError('failed-precondition', 'The configured AI model is unavailable to this server. Check the model ID and provider access.');
    if (error.status === 429) return new HttpsError('resource-exhausted', 'The AI provider quota or rate limit was reached. Check provider billing and limits.');
    if (error.status === 400 || error.status === 422) return new HttpsError('failed-precondition', 'The AI provider rejected the request configuration. Check the model settings and response schema in server logs.');
    return new HttpsError('unavailable', 'The AI provider failed to complete this request.');
  }
  const code = (error as { code?: unknown } | null)?.code;
  if (code === 7 || code === 'permission-denied') return new HttpsError('permission-denied', 'The backend cannot access its database. Check the runtime service account permissions.');
  if (code === 9 || code === 'failed-precondition') return new HttpsError('failed-precondition', 'The database query is not ready. Check Firestore indexes for the named default database.');
  if (code === 4 || code === 'deadline-exceeded') return new HttpsError('deadline-exceeded', 'A backend request timed out. Check request status before retrying.');
  return new HttpsError('internal', 'The server could not complete the request. Use the request reference to inspect its logs.');
}

export function errorMetadata(error: unknown) {
  const provider = error instanceof OpenAI.APIError ? error : undefined;
  const rawCode = (error as { code?: unknown } | null)?.code;
  return { code: publicError(error).code, errorType: error instanceof Error ? error.constructor.name : 'Unknown',
    providerStatus: provider?.status ?? null, providerRequestId: provider?.requestID ?? null,
    sourceCode: typeof rawCode === 'number' ? rawCode : typeof rawCode === 'string' ? rawCode.slice(0, 80).replace(/[^a-zA-Z0-9_-]/g, '') : null };
}
