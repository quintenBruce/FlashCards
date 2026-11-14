import { describe, it, expect } from 'vitest';
import { exactGrade, normalize, requeue, score } from '../src/domain';
import { cost, reserveAmount } from '../src/budget';
import { budgetMonth, nextOccurrence } from '../src/schedule';

describe('study invariants', () => {
  it('keeps accents and ñ while normalizing composition', () => {
    expect(normalize('  SI\u0301  ')).toBe('sí');
    expect(exactGrade('si', ['sí'])).toBe(false);
    expect(exactGrade('ano', ['año'])).toBe(false);
    expect(exactGrade('Good   morning', ['good morning'])).toBe(true);
  });
  it('spaces low and medium and finishes high', () => {
    expect(requeue(['a','b','c','d','e','f','g'], 'low')).toEqual(['b','c','a','d','e','f','g']);
    expect(requeue(['a','b','c','d','e','f','g'], 'medium')).toEqual(['b','c','d','e','f','a','g']);
    expect(requeue(['a'], 'low')).toEqual(['a']);
    expect(requeue(['a'], 'high')).toEqual([]);
  });
  it('does not count uncertain grades as incorrect', () => {
    expect(score('needs_review')).toBeNull(); expect(score('partial')).toBe(.5);
  });
});
describe('budget and schedules', () => {
  it('uses integer money and conservative reservations', () => {
    expect(cost('gpt-6.1-sol', 4000, 1000)).toBe(18000);
    expect(cost('gpt-6-luna', 800, 200)).toBe(180);
    expect(reserveAmount('gpt-6-luna', 'sí', 200)).toBeGreaterThan(100);
    expect(() => cost('unpriced', 1, 1)).toThrow();
  });
  it('resets at Chicago midnight', () => {
    expect(budgetMonth(Date.parse('2026-12-01T05:59:00Z'))).toBe('2026-11');
    expect(budgetMonth(Date.parse('2026-12-01T06:00:00Z'))).toBe('2026-12');
  });
  it('moves a spring gap to first valid minute', () => {
    const rule = { kind: 'daily' as const, timeZone: 'America/Chicago', hour: 2, minute: 30, weekdays: [], at: null };
    expect(nextOccurrence(rule, Date.parse('2027-03-14T06:00:00Z'))).toBe(Date.parse('2027-03-14T08:00:00Z'));
  });
  it('does not deliver twice during fall overlap', () => {
    const rule = { kind: 'daily' as const, timeZone: 'America/Chicago', hour: 1, minute: 30, weekdays: [], at: null };
    expect(nextOccurrence(rule, Date.parse('2026-11-01T06:31:00Z'))).toBe(Date.parse('2026-11-02T07:30:00Z'));
  });
});
