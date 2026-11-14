export const RATES: Record<string, { input: number; output: number }> = {
  'gpt-6.1-sol': { input: 2, output: 10 }, 'gpt-6-luna': { input: .1, output: .5 },
};
// Rates in USD / million tokens equal micro-USD / token. Round up, never down.
export function cost(model: string, input: number, output: number): number {
  const rate = RATES[model];
  if (!rate) throw new Error('Model rates must be configured before use');
  if (input < 0 || output < 0 || !Number.isFinite(input + output)) throw new Error('Invalid usage');
  return Math.ceil(input * rate.input + output * rate.output);
}
export function reserveAmount(model: string, payload: string, maxOutput: number): number {
  // UTF-8 byte count + framing allowance is a conservative token upper bound.
  const bound = Buffer.byteLength(payload, 'utf8') + 4096;
  if (bound > 100_000) throw new Error('Request too large; split the request');
  return cost(model, bound, maxOutput);
}
