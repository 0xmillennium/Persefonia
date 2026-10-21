import { expect, it } from 'vitest';
import { validateUnitExecution, unitEvidenceInventory } from './unit-contract.mjs';
const seedResults = () =>
  Array.from({ length: 100 }, (_, i) => ({ seed: i + 1, failure: null, passed: true, rest: true }));
function fixture() {
  return {
    discovered: [{ file: 'one.test.ts', name: 'one' }],
    report: {
      success: true,
      numTotalTests: 1,
      numFailedTests: 0,
      numPendingTests: 0,
      numTodoTests: 0,
      testResults: [
        {
          name: 'one.test.ts',
          assertionResults: [
            {
              ancestorTitles: [] as string[],
              title: 'one',
              fullName: 'one',
              status: 'passed',
              duration: 1,
            },
          ],
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
  if (mode === 'missing-file') f.discovered.push({ file: 'missing.test.ts', name: 'missing' });
  if (mode === 'failed') f.scalar[0]!.failure = 'guard' as unknown as null;
  if (mode === 'unrested') f.live[0]!.rest = false;
  expect(() => validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toThrow();
});

it('preserves repeated expanded names and rejects missing or substituted cases', () => {
  const f = fixture();
  f.discovered.push({ ...f.discovered[0]! });
  f.report.testResults[0]!.assertionResults.push({
    ...f.report.testResults[0]!.assertionResults[0]!,
  });
  f.report.numTotalTests = 2;
  expect(validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toHaveLength(2);
  f.discovered[1]!.name = 'substituted';
  expect(() => validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toThrow(
    /identities/,
  );
  f.discovered.pop();
  expect(() => validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toThrow(
    /identities/,
  );
});

it('reconciles nested collection names with structured native ancestor titles', () => {
  const f = fixture();
  f.discovered[0]!.name = 'suite > nested > one';
  const test = f.report.testResults[0]!.assertionResults[0]!;
  test.ancestorTitles = ['suite', 'nested'];
  test.fullName = 'suite nested one';
  expect(validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toHaveLength(1);
  test.ancestorTitles = ['other'];
  expect(() => validateUnitExecution(f.discovered, f.report, f.scalar, f.live)).toThrow(
    /identities/,
  );
});

it('compares serialized native inventories independently of object key order', () => {
  const collected = [{ name: 'suite > case', file: '/producer/frontend/source.test.ts' }];
  const serialized = [{ file: '/verifier/frontend/source.test.ts', name: 'suite > case' }];
  expect(unitEvidenceInventory(collected)).toEqual(unitEvidenceInventory(serialized));
  expect(unitEvidenceInventory([...collected, ...collected])).not.toEqual(
    unitEvidenceInventory(serialized),
  );
  expect(unitEvidenceInventory(collected)).not.toEqual(
    unitEvidenceInventory([{ ...serialized[0], name: 'substituted' }]),
  );
});
