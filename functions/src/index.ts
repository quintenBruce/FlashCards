import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { callable, db, root, region, id } from './core';
import { mutate, importCards } from './content';
import { syncReview, saveSession } from './study';
import { chat, chatStatus, approve, grade, openAiKey } from './ai';
import { saveReminder, reminderAction, registerDevice, dispatchDue } from './reminders';
import { exportPage, restorePage } from './backup';
import { cleanup } from './cleanup';

export const bootstrap = onCall({ region, enforceAppCheck: true, maxInstances: 1 }, async request => {
  const user = request.auth, configured = process.env.OWNER_EMAIL?.trim().toLowerCase();
  if (!configured || !user || user.token.email_verified !== true || user.token.firebase?.sign_in_provider !== 'google.com' || user.token.email?.toLowerCase() !== configured) throw new HttpsError('permission-denied', 'This app is restricted to its owner');
  await db.runTransaction(async tx => {
    const access = db.collection('access').doc(user.uid), existing = await tx.get(access);
    if (existing.exists && existing.get('enabled') !== true) throw new HttpsError('permission-denied', 'Account disabled');
    if (!existing.exists) tx.create(access, { enabled: true, deletionRequested: false, createdAt: Date.now() });
    tx.set(root(user.uid), { initialized: true }, { merge: true });
  });
  return { enabled: true };
});
export const mutateContent = callable('mutateContent', mutate);
export const importContent = callable('importContent', importCards);
export const syncReviewEvent = callable('syncReviewEvent', syncReview);
export const saveStudySession = callable('saveStudySession', saveSession);
export const sendDeckMessage = callable('sendDeckMessage', chat, [openAiKey.name]);
export const getChatRequest = callable('getChatRequest', chatStatus);
export const confirmProposal = callable('confirmProposal', approve);
export const gradeAnswer = callable('gradeAnswer', grade, [openAiKey.name]);
export const upsertReminder = callable('upsertReminder', saveReminder);
export const applyReminderAction = callable('applyReminderAction', reminderAction);
export const updateDevice = callable('updateDevice', registerDevice);
export const exportBackupPage = callable('exportBackupPage', exportPage);
export const restoreBackupPage = callable('restoreBackupPage', restorePage);
export const deleteConversation = callable('deleteConversation', async (uid, data) => {
  const conversationId = id(data.id);
  const conversationRef = root(uid).collection('conversations').doc(conversationId);
  const current = await conversationRef.get();
  await conversationRef.set({ id: conversationId, deckId: current.get('deckId') || '', deletedAt: Date.now() }, { merge: true });
  const turns = await root(uid).collection('turns').where('conversationId','==',conversationId).get();
  for (const turn of turns.docs) await turn.ref.delete();
  const proposals = await root(uid).collection('proposals').where('conversationId','==',conversationId).get();
  for (const proposal of proposals.docs) await proposal.ref.delete();
  const requests = await root(uid).collection('chatRequests').where('conversationId','==',conversationId).get();
  for (const request of requests.docs) await request.ref.delete();
  return { removed: true };
});
export const deleteAccount = callable('deleteAccount', async (uid, data) => {
  if (data.confirm !== 'DELETE') throw new HttpsError('invalid-argument', 'Type DELETE to confirm');
  await db.collection('access').doc(uid).set({ enabled: false, deletionRequested: true, deletionRequestedAt: Date.now() }, { merge: true });
  return { deletionRequested: true };
});
export const dispatchReminders = onSchedule({ region, schedule: '* * * * *', maxInstances: 1, timeoutSeconds: 540 }, dispatchDue);
export const cleanDeletedData = onSchedule({ region, schedule: 'every 24 hours', maxInstances: 1, timeoutSeconds: 540 }, cleanup);
