import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpsError } from 'firebase-functions/v2/https';

const memory = vi.hoisted(() => {
  const records = new Map<string, any>();
  let tail = Promise.resolve();
  const ref = (path: string): any => ({ path, id: path.split('/').pop(), collection: (name: string) => ({ doc: (key: string) => ref(`${path}/${name}/${key}`) }) });
  const snapshot = (r: any) => ({ exists: records.has(r.path), get: (key: string) => records.get(r.path)?.[key], data: () => records.get(r.path) });
  const db = { collection: (name: string) => ({ doc: (key: string) => ref(`${name}/${key}`) }),
    runTransaction: (fn: (tx: any) => Promise<any>) => {
      const result = tail.then(async () => {
        const writes: (() => void)[] = [];
        const tx = { get: async (r: any) => snapshot(r),
          set: (r: any,value: any) => writes.push(() => records.set(r.path,value)),
          create: (r: any,value: any) => writes.push(() => { if(records.has(r.path)) throw new Error('Already exists'); records.set(r.path,value); }),
          update: (r: any,value: any) => writes.push(() => records.set(r.path,{...records.get(r.path),...value})),
          delete: (r: any) => writes.push(() => records.delete(r.path)) };
        const value = await fn(tx); writes.forEach(write => write()); return value;
      });
      tail = result.then(() => undefined,() => undefined); return result;
    } };
  return { records, db, ref, clear: () => { records.clear(); tail = Promise.resolve(); } };
});
vi.mock('../src/core', () => ({
  db: memory.db, root: (uid: string) => memory.ref(`users/${uid}`),
  id: (v: any) => { if(typeof v !== 'string'||!/^[\w-]{1,100}$/.test(v)) throw new HttpsError('invalid-argument','Invalid ID'); return v; },
  text: (v: any,max=500,empty=false) => { if(typeof v !== 'string'||v.length>max||(!empty&&!v.trim())) throw new HttpsError('invalid-argument','Invalid text'); return v.trim(); },
  strings: (v: any) => { if(!Array.isArray(v)) throw new HttpsError('invalid-argument','Invalid list'); return v; },
  integer: (v: any,min=0,max=Number.MAX_SAFE_INTEGER) => { if(!Number.isSafeInteger(v)||v<min||v>max) throw new HttpsError('invalid-argument','Invalid number'); return v; },
  object: (v: any) => { if(!v||typeof v!=='object') throw new HttpsError('invalid-argument','Invalid object'); return v; },
  hash: (v: any) => JSON.stringify(v),
  version: (s: any,v: number) => { if(!s.exists||s.get('version')!==v) throw new HttpsError('failed-precondition','Changed'); },
  sameRequest: (s: any,f: string) => { if(s.exists&&s.get('fingerprint')!==f) throw new HttpsError('already-exists','Different request'); },
}));
import { approve } from '../src/ai';
import { mutate } from '../src/content';
import { syncReview } from '../src/study';
import { reserve, settle } from '../src/usage';
import { budgetMonth } from '../../shared/src/schedule';

const card = { deckId:'deck',term:'year',definition:'año',explanation:'',example:'',termAlternatives:[],definitionAlternatives:[],starred:false,order:0 };
beforeEach(() => {
  memory.clear(); delete process.env.AI_MONTHLY_MICRO_USD;
  memory.records.set('users/me/decks/deck',{id:'deck',version:1,deletedAt:null});
  memory.records.set('users/me/cards/card',{...card,id:'card',version:1,contentVersion:1,deletedAt:null});
});
describe('atomic AI approval', () => {
  function proposal() {
    memory.records.set('users/me/proposals/p',{deckId:'deck',deckVersion:1,state:'pending',expiresAt:Date.now()+60000,
      operations:[{kind:'edit_card',targetId:'card',expectedVersion:1,after:{...card,definition:'un año'},before:card}]});
  }
  it('rejects without changing content', async () => {
    proposal(); await approve('me',{proposalId:'p',approve:false});
    expect(memory.records.get('users/me/cards/card').definition).toBe('año');
    await expect(approve('me',{proposalId:'p',approve:true})).rejects.toHaveProperty('code','failed-precondition');
  });
  it('applies once and returns the same receipt on retry', async () => {
    proposal(); const first=await approve('me',{proposalId:'p',approve:true});
    expect(await approve('me',{proposalId:'p',approve:true})).toEqual(first);
    expect(memory.records.get('users/me/cards/card').version).toBe(2);
  });
  it('rejects the whole proposal if one target is stale', async () => {
    proposal(); memory.records.set('users/me/cards/card',{...card,version:2});
    await expect(approve('me',{proposalId:'p',approve:true})).rejects.toHaveProperty('code','failed-precondition');
    expect(memory.records.get('users/me/proposals/p').state).toBe('pending');
    expect(memory.records.get('users/me/decks/deck').version).toBe(1);
  });
});
describe('content and offline event conflicts', () => {
  it('metadata edits keep confidence content versions', async () => {
    await mutate('me',{requestId:'star',entity:'cards',action:'save',id:'card',expectedVersion:1,value:{...card,starred:true}});
    expect(memory.records.get('users/me/cards/card').contentVersion).toBe(1);
    expect(memory.records.get('users/me/decks/deck').version).toBe(2);
  });
  it('meaning changes invalidate old confidence', async () => {
    await mutate('me',{requestId:'edit',entity:'cards',action:'save',id:'card',expectedVersion:1,value:{...card,definition:'mes'}});
    expect(memory.records.get('users/me/cards/card').contentVersion).toBe(2);
    const result=await syncReview('me',{id:'e',sessionId:'s',cardId:'card',cardVersion:1,direction:'term',confidence:'high',expectedVersion:0,occurredAt:Date.now()});
    expect(result.applied).toBe(false); expect(memory.records.has('users/me/reviews/e')).toBe(true);
  });
  it('keeps newer server confidence and saves conflicting event history', async () => {
    memory.records.set('users/me/progress/card_term',{version:3,eventId:'newer',confidence:'low'});
    const event={id:'e',sessionId:'s',cardId:'card',cardVersion:1,direction:'term',confidence:'high',expectedVersion:1,occurredAt:Date.now()};
    expect((await syncReview('me',event)).applied).toBe(false);
    expect(await syncReview('me',event)).toEqual(memory.records.get('users/me/reviews/e').result);
    expect(memory.records.get('users/me/progress/card_term').confidence).toBe('low');
  });
});
describe('budget reservations', () => {
  it('serializes competing reservations before either can overspend', async () => {
    process.env.AI_MONTHLY_MICRO_USD='10000';
    const outcomes=await Promise.allSettled([reserve('me','a','gpt-6.1-sol','',0),reserve('me','b','gpt-6.1-sol','',0)]);
    expect(outcomes.filter(o=>o.status==='fulfilled')).toHaveLength(1);
    expect(memory.records.get(`users/me/usage/${budgetMonth(Date.now())}`).reserved).toBe(8192);
  });
  it('settles once and never reissues an uncertain provider request', async () => {
    await reserve('me','a','gpt-6.1-sol','',100);
    await expect(reserve('me','a','gpt-6.1-sol','',100)).rejects.toHaveProperty('code','already-exists');
    await settle('me','a','gpt-6.1-sol',100,10);await settle('me','a','gpt-6.1-sol',100,10);
    expect(memory.records.get(`users/me/usage/${budgetMonth(Date.now())}`)).toMatchObject({spent:300,reserved:0});
  });
});
