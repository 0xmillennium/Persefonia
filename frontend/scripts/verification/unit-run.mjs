import { spawnSync, execFileSync } from 'node:child_process';
import { rmSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  frontend,
  repository,
  writeJson,
  metadata,
  sourceContext,
  riskPlan,
} from './browser-runtime.mjs';
import {
  validateUnitExecution,
  validateUnitAttempts,
  validateUnitPhases,
} from './unit-contract.mjs';

import { policyHash } from './selection-policy.mjs';
const comprehensive = process.argv.slice(2).join(' ') === '--comprehensive';
const plan = comprehensive
  ? {
      schema: 1,
      policy: policyHash,
      required: ['ordinary', 'physics-live', 'physics-scalar'],
      manual: true,
    }
  : riskPlan();
const physicsProfile =
  comprehensive || plan.required.some((p) => p.startsWith('physics-')) ? 'deep' : 'ordinary';
process.env.PERSEFONIA_PHYSICS_PROFILE = physicsProfile;

if ((!comprehensive && process.argv.length !== 2) || process.env.IDENTITY_SEED)
  throw new Error('Mandatory unit verification cannot use filters or a single seed');
const output = join(frontend, 'test-results');
for (const file of [
  'unit.json',
  'unit-receipt.json',
  'unit-discovery.json',
  'unit-attempts.json',
  'unit-phases.json',
  'segmented-identity/unit-stress-summary.json',
  'segmented-identity/live-stress-summary.json',
])
  rmSync(join(output, file), { force: true });
const cli = join(frontend, 'node_modules/vitest/vitest.mjs');
const context = sourceContext();
const started = performance.now();
const discovery = spawnSync(
  process.execPath,
  [cli, 'list', '--no-static-parse', '--json', '--allowOnly=false'],
  {
    cwd: frontend,
    encoding: 'utf8',
  },
);
if (discovery.status !== 0) throw new Error(`Unit discovery failed: ${discovery.stderr}`);
const selected = JSON.parse(discovery.stdout);
writeJson(join(output, 'unit-discovery.json'), selected);
const discoveryMs = performance.now() - started;
const execution = spawnSync(
  process.execPath,
  [
    cli,
    'run',
    '--allowOnly=false',
    '--retry=0',
    '--reporter=default',
    '--reporter=json',
    '--reporter=./scripts/verification/unit-attempts.ts',
    '--outputFile=test-results/unit.json',
  ],
  {
    cwd: frontend,
    stdio: 'inherit',
    env: {
      ...process.env,
      PERSEFONIA_UNIT_ATTEMPTS: join(output, 'unit-attempts.json'),
      PERSEFONIA_UNIT_PHASES: join(output, 'unit-phases.json'),
    },
  },
);
if (execution.status !== 0) process.exit(execution.status ?? 1);
const reconciliation = performance.now();
const read = (path) => JSON.parse(readFileSync(join(output, path), 'utf8'));
const cases = validateUnitExecution(
  selected,
  read('unit.json'),
  read('segmented-identity/unit-stress-summary.json'),
  read('segmented-identity/live-stress-summary.json'),
  physicsProfile,
);
const attempts = read('unit-attempts.json');
validateUnitAttempts(cases, attempts);
const phases = read('unit-phases.json');
validateUnitPhases(cases, phases);
if (JSON.stringify(sourceContext()) !== JSON.stringify(context))
  throw new Error('Source changed during frontend verification');
writeJson(join(output, 'unit-receipt.json'), {
  sourceContext: context,
  schema: 2,
  policy: policyHash,
  selectionPlan: plan,
  physicsProfile,
  profile: comprehensive ? 'comprehensive' : 'ordinary',
  status: 'passed',
  sourceSha: execFileSync('git', ['rev-parse', 'HEAD'], {
    cwd: repository,
    encoding: 'utf8',
  }).trim(),
  runner: metadata(),
  discoveryMs,
  executionMs: reconciliation - started - discoveryMs,
  reconciliationMs: performance.now() - reconciliation,
  selectedFiles: [...new Set(selected.map((r) => r.file))],
  cases: cases.map(({ file, fullName, status, duration }) => ({
    file,
    fullName,
    status,
    duration,
  })),
  attempts,
  phases,
});
