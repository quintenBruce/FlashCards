import { beforeEach, describe, expect, it, vi } from 'vitest';
import OpenAI from 'openai';
import { HttpsError } from 'firebase-functions/v2/https';

const provider = vi.hoisted(() => ({ create: vi.fn(), reserve: vi.fn(), settle: vi.fn(), release: vi.fn() }));
const memory = vi.hoisted(() => {
  const records = new Map<string, any>();
  let tail = Promise.resolve();
  const snapshot = (path: string) => ({ exists: records.has(path), data: () => records.get(path), get: (key: string) => records.get(path)?.[key] });
  const collection = (path: string): any => ({ doc: (key: string) => ref(`${path}/${key}`),
    where: () => ({ orderBy: () => ({ limit: () => ({ get: async () => ({ docs: [] }) }) }) }) });
  const ref = (path: string): any => ({ path, collection: (name: string) => collection(`${path}/${name}`),
    get: async () => snapshot(path), update: async (value: any) => { records.set(path,{...records.get(path),...value}); } });
  const db = { collection, runTransaction: (fn: (tx: any) => Promise<any>) => {
    const result = tail.then(async () => {
      const writes: (() => void)[] = [];
      const tx = { get: async (r: any) => snapshot(r.path),
        create: (r: any,v: any) => writes.push(() => { if(records.has(r.path))throw new Error('Duplicate'); records.set(r.path,v); }),
        set: (r: any,v: any) => writes.push(() => records.set(r.path,v)),
        update: (r: any,v: any) => writes.push(() => records.set(r.path,{...records.get(r.path),...v})) };
      const value = await fn(tx); writes.forEach(w => w()); return value;
    });
    tail = result.then(() => undefined,() => undefined); return result;
  } };
  return { records, ref, db, clear: () => { records.clear(); tail=Promise.resolve(); } };
});
vi.mock('openai', async importOriginal => {
  const sdk: any = await importOriginal();
  class FakeOpenAI extends sdk.default {
    constructor(options: any) { super({...options,apiKey:'test'}); this.responses.create=provider.create as any; }
  }
  return {...sdk,default:FakeOpenAI};
});
vi.mock('firebase-functions/params', () => ({ defineSecret: () => ({ name:'OPENAI_API_KEY',value:()=>'test' }) }));
vi.mock('../src/usage', () => ({ reserve:provider.reserve,settle:provider.settle,releaseRejected:provider.release }));
vi.mock('../src/core', () => ({ db:memory.db,root:(uid:string)=>memory.ref(`users/${uid}`),
  hash:(v:unknown)=>JSON.stringify(v),object:(v:unknown)=>v,
  id:(v:any)=>{if(typeof v!=='string'||!/^[\w-]{1,100}$/.test(v))throw new HttpsError('invalid-argument','Invalid ID');return v;},
  text:(v:any,max=500)=>{if(typeof v!=='string'||!v.trim()||v.length>max)throw new HttpsError('invalid-argument','Invalid text');return v.trim();},
  sameRequest:(snapshot:any,fingerprint:string)=>{if(snapshot.get('fingerprint')!==fingerprint)throw new HttpsError('already-exists','Request ID already used');},
  version:vi.fn(),strings:vi.fn(),integer:vi.fn() }));
import { chat, chatStatus } from '../src/ai';

const request = {requestId:'request',deckId:'draft',conversationId:'conversation',message:'Create travel cards'};
const completed = {status:'completed',output_text:JSON.stringify({reply:'Review these suggestions.',operations:[]}),usage:{input_tokens:50,output_tokens:20}};
beforeEach(() => {
  memory.clear(); vi.clearAllMocks();
  memory.records.set('access/me',{enabled:true});
  provider.create.mockResolvedValue(completed);
});
describe('chat request recovery', () => {
  it('returns the saved turn directly and replays it without another provider call', async () => {
    expect(await chat('me',request)).toMatchObject({state:'succeeded',turn:{user:request.message,assistant:'Review these suggestions.'}});
    expect(await chat('me',request)).toMatchObject({state:'succeeded'});
    expect(provider.create).toHaveBeenCalledTimes(1);
  });
  it('does not reissue a request while the original provider call is in flight', async () => {
    let finish!: (value:any)=>void;
    provider.create.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
    const first=chat('me',request);
    await vi.waitFor(()=>expect(provider.create).toHaveBeenCalledTimes(1));
    expect(await chat('me',request)).toMatchObject({state:'pending'});
    finish(completed); await first;
    expect(provider.create).toHaveBeenCalledTimes(1);
  });
  it('surfaces provider authentication failure without exposing its error body', async () => {
    provider.create.mockRejectedValueOnce(new OpenAI.AuthenticationError(401,{message:'private prompt and key'},'private prompt and key',new Headers()));
    const result=await chat('me',request);
    expect(result).toMatchObject({state:'failed',error:{code:'failed-precondition'}});
    expect(JSON.stringify(result)).not.toContain('private prompt');
    expect(provider.release).toHaveBeenCalledWith('me','request');
    expect(await chat('me',request)).toMatchObject({state:'failed'});
    expect(provider.create).toHaveBeenCalledTimes(1);
  });
  it('keeps the reservation on a timeout and preserves its recoverable request ID', async () => {
    provider.create.mockRejectedValueOnce(new OpenAI.APIConnectionTimeoutError());
    expect(await chat('me',request)).toMatchObject({requestId:'request',state:'failed',error:{code:'deadline-exceeded'}});
    expect(provider.release).not.toHaveBeenCalled();
  });
  it('rejects reusing a request ID with different content', async () => {
    await chat('me',request);
    await expect(chat('me',{...request,message:'different'})).rejects.toHaveProperty('code','already-exists');
    expect(provider.create).toHaveBeenCalledTimes(1);
  });
  it('distinguishes missing requests from requests interrupted before a receipt', async () => {
    expect(await chatStatus('me',request)).toMatchObject({state:'not_found'});
    memory.records.set('users/me/chatRequests/request',{state:'pending',createdAt:Date.now()-600000});
    expect(await chatStatus('me',request)).toMatchObject({state:'uncertain'});
    expect(provider.create).not.toHaveBeenCalled();
  });
});
