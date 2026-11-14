export type Direction = 'term' | 'definition';
export type Confidence = 'low' | 'medium' | 'high';
export type Verdict = 'correct' | 'partial' | 'incorrect' | 'needs_review';
export interface Deck {
  id: string; title: string; description: string; termLanguage: string; definitionLanguage: string;
  spanishVariety: string; version: number; archived: boolean; deletedAt: number | null; updatedAt: number;
}
export interface Card {
  id: string; deckId: string; term: string; definition: string; explanation: string; example: string;
  termAlternatives: string[]; definitionAlternatives: string[]; starred: boolean; order: number;
  version: number; contentVersion?: number; deletedAt: number | null; updatedAt: number;
}
export interface Progress {
  id: string; cardId: string; direction: Direction; confidence: Confidence; cardVersion: number;
  version: number; eventId: string; updatedAt: number;
}
export interface ReviewEvent {
  id: string; sessionId: string; cardId: string; cardVersion: number; direction: Direction;
  confidence: Confidence; expectedVersion: number; occurredAt: number; undoEventId?: string;
}
export interface Schedule {
  kind: 'once' | 'daily' | 'weekdays'; timeZone: string; hour: number; minute: number;
  weekdays: number[]; at: number | null;
}
export interface Reminder {
  id: string; title: string; deckIds: string[]; filter: string; schedule: Schedule;
  enabled: boolean; nextAt: number | null; version: number; updatedAt: number;
}
export function normalize(value: string): string {
  return value.normalize('NFC').trim().replace(/\s+/gu, ' ').toLocaleLowerCase('es');
}
export function accepted(card: Card, direction: Direction): string[] {
  return direction === 'term' ? [card.definition, ...card.definitionAlternatives] : [card.term, ...card.termAlternatives];
}
export function exactGrade(answer: string, options: string[]): boolean {
  return options.some(option => normalize(option) === normalize(answer));
}
export function score(verdict: Verdict): number | null {
  return verdict === 'needs_review' ? null : verdict === 'correct' ? 1 : verdict === 'partial' ? .5 : 0;
}
export function requeue(queue: string[], confidence: Confidence): string[] {
  if (!queue.length) return [];
  const [current, ...rest] = queue;
  if (confidence !== 'high') rest.splice(Math.min(rest.length, confidence === 'low' ? 2 : 5), 0, current);
  return rest;
}
