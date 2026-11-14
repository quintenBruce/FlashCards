import { auth, db, root } from './core';

export async function cleanup() {
  const disabled = await db.collection('access').where('deletionRequested','==',true).get();
  for (const account of disabled.docs) {
    if (account.get('deletionRequestedAt') > Date.now() - 600000) continue;
    await db.recursiveDelete(root(account.id));
    try { await auth.deleteUser(account.id); } catch (e: any) { if (e.code !== 'auth/user-not-found') throw e; }
    await account.ref.update({ deletionRequested: false, deletedAt: Date.now() });
  }
  const accounts = await db.collection('access').where('enabled','==',true).get();
  for (const account of accounts.docs) {
    for (const collection of ['decks','cards']) {
      const rows = await root(account.id).collection(collection).get();
      for (const row of rows.docs) {
        if (!row.get('purgeRequested') && !(row.get('deletedAt') && row.get('deletedAt') < Date.now() - 30 * 86400000)) continue;
        if (collection === 'decks') {
          for (const childCollection of ['cards','conversations','proposals']) {
            const children = await root(account.id).collection(childCollection).where('deckId','==',row.id).get();
            for (const child of children.docs) {
              if (childCollection === 'conversations') {
                const turns = await root(account.id).collection('turns').where('conversationId','==',child.id).get();
                for (const turn of turns.docs) await turn.ref.delete();
              }
              await db.recursiveDelete(child.ref);
            }
          }
          const reminders = await root(account.id).collection('reminders').get();
          for (const reminder of reminders.docs) if (reminder.get('deckIds')?.includes(row.id)) {
            const remaining = reminder.get('deckIds').filter((d: string) => d !== row.id);
            await reminder.ref.update({ deckIds: remaining, enabled: remaining.length > 0 && reminder.get('enabled'), version: reminder.get('version') + 1 });
          }
        } else {
          for (const direction of ['term','definition']) await root(account.id).collection('progress').doc(`${row.id}_${direction}`).delete();
        }
        await row.ref.delete();
      }
    }
  }
}
