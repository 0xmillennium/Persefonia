import { expect, it } from 'vitest';
import {
  digest,
  joinReceipts,
  validateAcceptance,
  validateOutcomes,
  reportTests,
  sameInventory,
  partitions,
} from './browser-contract.mjs';
import { policyHash, selectionPolicy } from './selection-policy.mjs';
const identity = { sourceSha: 'a'.repeat(40), artifactSha256: 'b'.repeat(64) };
type Outcome = {
  id: string;
  project: string;
  file: string;
  title: string;
  expectedStatus: string;
  status: string;
  annotations: { type: string; description: string }[];
  results: { status: string; retry: number; duration: number }[];
};
function fixture() {
  const runner = { run: '100', attempt: '1' };
  const sourceContext = {
    sourceSha: identity.sourceSha,
    tree: 'c'.repeat(40),
    policy: 'd'.repeat(64),
  };
  const receipts = partitions.map(
    (partition: { project: string; suite: string; name: string; shard: string }) => {
      const source = selectionPolicy.native.find(
        (p: { project: string }) => p.project === partition.project,
      )!;
      const outcomes = source.cases.map((c: { file: string; title: string }, index: number) => ({
        ...c,
        id: `${partition.project}:${index}`,
        project: partition.project,
        expectedStatus: 'passed',
        status: 'expected',
        annotations: [] as { type: string; description: string }[],
        results: [{ status: 'passed', retry: 0, duration: 1 }],
      }));
      const selected = outcomes.map((r: { id: string }) => r.id);
      return {
        environment: {
          browsers: { [source.engine]: { sha256: 'e'.repeat(64) } },
          ffmpeg: 'qualified ffmpeg',
          ffprobe: 'qualified ffprobe',
        },
        identity: { ...identity },
        schema: 3,
        policy: policyHash,
        partition: { ...partition },
        status: 'passed',
        exitCode: 0,
        errors: [],
        runner: { ...runner },
        sourceContext: { ...sourceContext },
        outcomes,
        selected,
        selectionHash: digest([...selected].sort()),
      };
    },
  );
  const rows: Outcome[] = receipts.flatMap((r: { outcomes: Outcome[] }) =>
    structuredClone(r.outcomes),
  );
  const report = (rows: Outcome[]) => ({
    config: {
      forbidOnly: true,
      fullyParallel: false,
      workers: 1,
      projects: selectionPolicy.native.map((p: { project: string }) => ({
        name: p.project,
        retries: 0,
        repeatEach: 1,
      })),
    },
    errors: [],
    suites: [
      {
        specs: rows.map((r) => ({
          id: r.id.split(':')[1],
          file: r.file,
          title: r.title,
          tests: [
            {
              projectName: r.project,
              expectedStatus: r.expectedStatus,
              status: r.status,
              annotations: r.annotations,
              results: r.results,
            },
          ],
        })),
      },
    ],
  });
  return {
    native: {
      discovery: report(rows.map((r) => ({ ...r, results: [] }))),
      execution: report(rows),
    },
    schema: 3,
    policy: policyHash,
    profiles: ['ordinary'],
    status: 'passed',
    identity,
    runner,
    sourceContext,
    receipts,
    inventory: {
      ordinary: rows.map((r) => ({ ...r, results: [] })),
    },
    partitions: receipts.length,
    selected: receipts.reduce((n: number, r: { selected: unknown[] }) => n + r.selected.length, 0),
  };
}
it('admits exact independent policy/discovery/outcomes for four native context identities', () => {
  const f = fixture();
  expect(validateAcceptance(f, identity)).toMatchObject({ partitions: 4, selected: f.selected });
});
it.each([
  'missing',
  'extra',
  'duplicate',
  'failed',
  'cancelled',
  'exit',
  'empty',
  'old-schema',
  'policy',
  'source',
  'artifact',
  'omitted-test',
  'retry',
  'flaky',
  'skip',
  'fingerprint',
  'forged-title',
  'wrong-engine',
  'wrong-shard',
  'run',
  'attempt',
  'tree',
  'count',
  'profile',
  'discovery',
  'unsupported-engine',
  'missing-decoder',
])('rejects %s qualification', (mode) => {
  const f = fixture(),
    r = f.receipts[0]!;
  switch (mode) {
    case 'missing':
      f.receipts.pop();
      break;
    case 'extra':
      f.receipts.push(structuredClone(r));
      break;
    case 'duplicate':
      f.receipts[1] = structuredClone(r);
      break;
    case 'failed':
      r.status = 'failed';
      break;
    case 'cancelled':
      r.outcomes[0]!.results[0]!.status = 'interrupted';
      break;
    case 'exit':
      r.exitCode = 1;
      break;
    case 'empty':
      r.outcomes = [];
      r.selected = [];
      break;
    case 'old-schema':
      r.schema = 1;
      break;
    case 'policy':
      r.policy = 'old-policy';
      break;
    case 'source':
      r.sourceContext.sourceSha = 'c'.repeat(40);
      break;
    case 'artifact':
      r.identity = { ...identity, artifactSha256: 'd'.repeat(64) };
      break;
    case 'omitted-test':
      r.selected.pop();
      break;
    case 'retry':
      r.outcomes[0]!.results.push({ status: 'passed', retry: 1, duration: 1 });
      break;
    case 'flaky':
      r.outcomes[0]!.status = 'flaky';
      break;
    case 'skip':
      r.outcomes[0]!.results[0]!.status = 'skipped';
      break;
    case 'fingerprint':
      r.selectionHash = 'forged';
      break;
    case 'forged-title':
      r.outcomes[0]!.title = 'another case';
      break;
    case 'wrong-engine':
      r.partition.project = 'webkit-desktop';
      break;
    case 'wrong-shard':
      r.partition.shard = '1/2';
      break;
    case 'run':
      r.runner.run = '99';
      break;
    case 'attempt':
      r.runner.attempt = '2';
      break;
    case 'tree':
      r.sourceContext.tree = 'e'.repeat(40);
      break;
    case 'count':
      f.selected++;
      break;
    case 'profile':
      f.profiles.push('media');
      break;
    case 'unsupported-engine':
      r.environment.browsers = {};
      break;
    case 'missing-decoder':
      r.environment.ffprobe = '';
      break;
    case 'discovery':
      f.inventory.ordinary.pop();
      break;
  }
  expect(() => validateAcceptance(f, identity), mode).toThrow();
});
it('rejects self-consistent omitted case or renamed title even when submitted discovery agrees', () => {
  for (const mode of ['omit', 'rename']) {
    const f = fixture(),
      r = f.receipts[0]!;
    if (mode === 'omit') r.outcomes.pop();
    else r.outcomes[0]!.title = 'forged';
    r.selected = r.outcomes.map((row: { id: string }) => row.id);
    r.selectionHash = digest([...r.selected].sort());
    f.inventory.ordinary = f.receipts.flatMap((r: { outcomes: unknown[] }) =>
      structuredClone(r.outcomes),
    );
    f.selected = f.receipts.reduce(
      (n: number, r: { selected: unknown[] }) => n + r.selected.length,
      0,
    );
    expect(() => validateAcceptance(f, identity)).toThrow();
  }
});
it('keeps discovery, empty, duplicate and first-attempt validation independent', () => {
  expect(() => sameInventory([], [])).toThrow();
  expect(() => sameInventory(['one'], ['one', 'one'])).toThrow();
  expect(() => reportTests({ config: { forbidOnly: false } })).toThrow('Focused');
  expect(() => reportTests({ config: { forbidOnly: true, fullyParallel: true } })).toThrow(
    'scheduling',
  );
  const f = fixture();
  expect(joinReceipts(f.inventory, f.receipts, identity).partitions).toBe(4);
  expect(validateOutcomes(f.receipts[0]!.outcomes).skipped).toBe(0);
});
