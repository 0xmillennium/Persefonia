import { expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { digest, validateAcceptance, partitionKey } from './browser-contract.mjs';
import { policyHash, requiredPartitions, selectionPolicy } from './selection-policy.mjs';
const identity = { sourceSha: 'a'.repeat(40), artifactSha256: 'b'.repeat(64) };
function fixture() {
  const sourceContext = {
    sourceSha: identity.sourceSha,
    tree: 'c'.repeat(40),
    policy: 'd'.repeat(64),
  };
  const runner = { run: '100', attempt: '1' };
  const receipts = requiredPartitions(['ordinary', 'admin']).map(
    (partition: { suite: string; project: string }) => {
      const owner = (
        partition.suite === 'admin' ? selectionPolicy.adminNative : selectionPolicy.native
      ).find((p: { project: string }) => p.project === partition.project)!;
      const outcomes = owner.cases.map((c: { file: string; title: string }, index: number) => ({
        ...c,
        id: `${partition.project}:${index}`,
        project: partition.project,
        expectedStatus: 'passed',
        status: 'expected',
        annotations: [],
        results: [{ status: 'passed', retry: 0, duration: 1 }],
      }));
      const selected = outcomes.map((r: { id: string }) => r.id);
      return {
        schema: 2,
        policy: policyHash,
        identity: { ...identity },
        partition,
        status: 'passed',
        exitCode: 0,
        errors: [],
        runner: { ...runner, job: partition.suite === 'admin' ? 'build-jvm' : 'browser' },
        sourceContext,
        environment: {
          browsers: { [owner.engine]: { sha256: 'e'.repeat(64) } },
          ffmpeg: 'qualified',
          ffprobe: 'qualified',
        },
        outcomes,
        selected,
        selectionHash: digest([...selected].sort()),
      };
    },
  );
  return {
    schema: 2,
    policy: policyHash,
    profiles: ['ordinary', 'admin'],
    status: 'passed',
    identity,
    runner,
    sourceContext,
    receipts,
    inventory: Object.fromEntries(
      ['ordinary', 'admin'].map((suite) => [
        suite,
        receipts
          .filter((r: { partition: { suite: string } }) => r.partition.suite === suite)
          .flatMap((r: { outcomes: unknown[] }) => structuredClone(r.outcomes)),
      ]),
    ),
    partitions: receipts.length,
    selected: receipts.reduce((n: number, r: { selected: unknown[] }) => n + r.selected.length, 0),
  };
}
it('joins admin only with exact own-workspace native forms and public artifact contracts', () => {
  expect(validateAcceptance(fixture(), identity).partitions).toBe(5);
});
it.each([
  'missing',
  'public-substitute',
  'skip',
  'duplicate',
  'wrong-case',
  'wrong-attempt',
  'foreign-workspace',
])('rejects %s required admin evidence', (mode) => {
  const f = fixture(),
    admin = f.receipts.at(-1)!;
  if (mode === 'missing') f.receipts.pop();
  if (mode === 'public-substitute')
    f.receipts[f.receipts.length - 1] = structuredClone(f.receipts[0]!);
  if (mode === 'skip') admin.outcomes[0]!.results[0]!.status = 'skipped';
  if (mode === 'duplicate') admin.outcomes.push(structuredClone(admin.outcomes[0]!));
  if (mode === 'wrong-case') admin.outcomes[0]!.title = 'public smoke';
  if (mode === 'foreign-workspace') admin.runner.job = 'browser';
  if (mode === 'wrong-attempt') admin.runner.attempt = '2';
  expect(() => validateAcceptance(f, identity)).toThrow();
});
it('keeps native diagnostics trace-free and the admin context explicit', () => {
  const source = readFileSync(
    new URL('../../playwright.admin.required.config.ts', import.meta.url),
    'utf8',
  );
  expect(source).toContain("trace: 'off'");
  expect(requiredPartitions(['admin']).map(partitionKey)).toEqual(['admin-preview-1of1']);
});
