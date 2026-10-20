import { expect, it } from 'vitest';
import { validateUnitExecution } from './unit-contract.mjs';
const seedResults = () =>
  Array.from({ length: 100 }, (_, i) => ({ seed: i + 1, failure: null, passed: true, rest: true }));
function fixture() {
  return {
    discovered: [{ file: 'one.test.ts' }],
    report: {
      success: true,
      numTotalTests: 1,
      numFailedTests: 0,
      numPendingTests: 0,
      numTodoTests: 0,
      testResults: [
        {
          name: 'one.test.ts',
          assertionResults: [{ fullName: 'one', status: 'passed', duration: 1 }],
        },
      ],
    },
    scalar: seedResults(),
    live: seedResults(),
  };
}
it('retains every scalar and coalesced seed and allows future additional cases', () => {
  const f = fixture();
  expect(validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toHaveLength(1);
  f.scalar.push({ seed: 101, failure: null, passed: true, rest: true });
  f.live.push({ seed: 101, failure: null, passed: true, rest: true });
  expect(validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toHaveLength(1);
});
it.each([
  'single-seed',
  'missing-live',
  'duplicate',
  'skipped',
  'missing-file',
  'failed',
  'unrested',
])('rejects %s mandatory verification', (mode) => {
  const f = fixture();
  if (mode === 'single-seed') {
    f.scalar = f.scalar.slice(0, 1);
    f.live = f.live.slice(0, 1);
  }
  if (mode === 'missing-live') f.live.pop();
  if (mode === 'duplicate') f.scalar.push(f.scalar[0]!);
  if (mode === 'skipped') f.report.testResults[0]!.assertionResults[0]!.status = 'pending';
  if (mode === 'missing-file') f.discovered.push({ file: 'missing.test.ts' });
  if (mode === 'failed') f.scalar[0]!.failure = 'guard' as unknown as null;
  if (mode === 'unrested') f.live[0]!.rest = false;
  expect(() => validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toThrow();
});
