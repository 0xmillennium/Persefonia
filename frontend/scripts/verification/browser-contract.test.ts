import { expect, it } from 'vitest';
import {
  digest,
  sameInventory,
  reportTests,
  validateOutcomes,
  validateReceipt,
  joinReceipts,
  validateAcceptance,
  partitions,
} from './browser-contract.mjs';

const identity = { sourceSha: 'a'.repeat(40), artifactSha256: 'b'.repeat(64) };
const row = (id: string, project = 'desktop-1440') => ({
  id,
  project,
  file: `${id}.spec.ts`,
  title: id,
  expectedStatus: 'passed',
  status: 'expected',
  annotations: [] as { type: string; description: string }[],
  results: [{ status: 'passed', duration: 1, retry: 0 }],
});
function fixture() {
  const receipts = partitions.map((partition, index) => {
    const outcomes = [
      row(String(index), partition.project === '*' ? 'firefox-desktop' : partition.project),
    ];
    const selected = outcomes.map((r) => r.id);
    return {
      schema: 1,
      identity: { ...identity },
      partition: { ...partition },
      status: 'passed',
      exitCode: 0,
      errors: [],
      outcomes,
      selected,
      selectionHash: digest([...selected].sort()),
    };
  });
  const inventory = {
    chromium: receipts
      .filter((r) => r.partition.suite === 'chromium')
      .flatMap((r) => structuredClone(r.outcomes)),
    cross: receipts
      .filter((r) => r.partition.suite === 'cross')
      .flatMap((r) => structuredClone(r.outcomes)),
  };
  return { receipts, inventory };
}
it('joins complete independent discovery and first-attempt execution without fixed test counts', () => {
  const f = fixture();
  expect(joinReceipts(f.inventory, f.receipts, identity).selected).toBe(partitions.length);
  f.receipts[0]!.outcomes.push(row('new'));
  f.receipts[0]!.selected.push('new');
  f.receipts[0]!.selectionHash = digest([...f.receipts[0]!.selected].sort());
  f.inventory.chromium.push(row('new'));
  expect(joinReceipts(f.inventory, f.receipts, identity).selected).toBe(partitions.length + 1);
});
it.each([
  'missing',
  'duplicate',
  'failed',
  'cancelled',
  'skipped-job',
  'empty',
  'corrupt',
  'sha',
  'artifact',
  'omitted-test',
  'retries',
  'unexpected-skip',
  'fingerprint',
  'forged-skip-identity',
])('rejects %s qualification', (mode) => {
  const f = fixture(),
    r = f.receipts[0]!;
  switch (mode) {
    case 'missing':
      f.receipts.pop();
      break;
    case 'duplicate':
      f.receipts.push(structuredClone(r));
      break;
    case 'failed':
      r.status = 'failed';
      break;
    case 'cancelled':
      r.outcomes[0]!.results[0]!.status = 'interrupted';
      break;
    case 'skipped-job':
      r.exitCode = 1;
      break;
    case 'empty':
      r.selected = [];
      r.outcomes = [];
      break;
    case 'corrupt':
      r.schema = 99;
      break;
    case 'sha':
      r.identity.sourceSha = 'c'.repeat(40);
      break;
    case 'artifact':
      r.identity.artifactSha256 = 'd'.repeat(64);
      break;
    case 'omitted-test':
      f.inventory.chromium.push(row('missing'));
      break;
    case 'retries':
      r.outcomes[0]!.results.push({ status: 'passed', duration: 1, retry: 1 });
      break;
    case 'unexpected-skip':
      r.outcomes[0]!.results[0]!.status = 'skipped';
      r.outcomes[0]!.status = 'skipped';
      break;
    case 'fingerprint':
      r.selectionHash = 'corrupt';
      break;
    case 'forged-skip-identity':
      r.outcomes[0]!.title = 'another test';
      break;
  }
  expect(() => joinReceipts(f.inventory, f.receipts, identity)).toThrow();
});
it('accepts only explicit skip predicates and reasons while allowing a capability-dependent skip', () => {
  const skip = row('skipped');
  skip.status = 'skipped';
  skip.expectedStatus = 'skipped';
  skip.results[0]!.status = 'skipped';
  skip.annotations.push({ type: 'skip', description: 'Native touch' });
  const policy = [
    {
      file: skip.file,
      title: skip.title,
      projects: [skip.project],
      reason: 'Native touch',
      optional: false,
    },
  ];
  expect(validateOutcomes([row('passed'), skip], policy)).toMatchObject({ passed: 1, skipped: 1 });
  expect(() => validateOutcomes([skip], policy)).toThrow('No tests executed');
  skip.annotations[0]!.description = 'different';
  expect(() => validateOutcomes([row('passed'), skip], policy)).toThrow('Unexpected skip');
  expect(() => sameInventory(['one'], ['one', 'one'])).toThrow('Duplicate');
  expect(() => validateReceipt({}, identity)).toThrow();
});
it('rejects corrupt and focused native reports before treating discovery as execution', () => {
  expect(() => reportTests({ config: { forbidOnly: false } })).toThrow('Focused');
  expect(() => reportTests({ config: { forbidOnly: true, fullyParallel: true } })).toThrow(
    'scheduling',
  );
});

it('keeps each project/file atomic even when the union of selected IDs is complete', () => {
  const f = fixture();
  f.receipts[0]!.outcomes[0]!.file = 'shared.spec.ts';
  f.receipts[1]!.outcomes[0]!.file = 'shared.spec.ts';
  f.inventory.chromium[0]!.file = 'shared.spec.ts';
  f.inventory.chromium[1]!.file = 'shared.spec.ts';
  expect(() => joinReceipts(f.inventory, f.receipts, identity)).toThrow('File split');
});

it('admission rejects forged, duplicate, omitted and mixed-attempt receipts using the source-join contract', () => {
  const fixtureData = fixture();
  const runner = { run: 'controlled-fixture', attempt: '1' };
  const sourceContext = { sourceSha: identity.sourceSha, policy: 'fixture-policy' };
  const acceptance = {
    schema: 1,
    status: 'passed',
    identity,
    inventory: fixtureData.inventory,
    receipts: fixtureData.receipts.map((receipt) => ({
      ...receipt,
      runner: { ...runner },
      sourceContext: { ...sourceContext },
    })),
    runner,
    sourceContext,
    selected: partitions.length,
    partitions: partitions.length,
  };
  expect(validateAcceptance(acceptance, identity).partitions).toBe(8);
  for (const variant of ['duplicate', 'omitted', 'count', 'attempt', 'policy', 'candidate']) {
    const changed = structuredClone(acceptance);
    if (variant === 'duplicate') changed.receipts[1] = changed.receipts[0]!;
    else if (variant === 'omitted') changed.receipts.pop();
    else if (variant === 'count') changed.selected++;
    else if (variant === 'attempt') changed.receipts[0]!.runner.attempt = '2';
    else if (variant === 'policy') changed.receipts[0]!.sourceContext.policy = 'forged';
    else changed.receipts[0]!.identity.artifactSha256 = 'c'.repeat(64);
    expect(() => validateAcceptance(changed, identity), variant).toThrow();
  }
});
