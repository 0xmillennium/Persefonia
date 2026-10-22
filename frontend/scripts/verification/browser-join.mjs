import { readEvidence } from './browser-runtime.mjs';
import { readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { reportTests, joinReceipts } from './browser-contract.mjs';
import {
  repository,
  identity,
  metadata,
  sourceContext,
  playwright,
  writeJson,
  riskPlan,
} from './browser-runtime.mjs';

import {
  policyHash,
  nativeProfiles,
  requiredPartitions,
  validateNativeSelection,
} from './selection-policy.mjs';

const plan = riskPlan();
const profiles = nativeProfiles(plan);
const aggregationStarted = performance.now();
const directory = resolve(process.argv[2] ?? join(repository, 'app/build/browser/verification'));
const inventory = {};
for (const suite of profiles) {
  const path = join(directory, `${suite}-complete-discovery.json`);
  const discovery = playwright(suite, [], path, true);
  if (discovery.exitCode !== 0) throw new Error(`Independent ${suite} discovery failed`);
  inventory[suite] = reportTests(readEvidence(path));
}
const names = readdirSync(directory);
if (names.length > 100) throw new Error('Oversized browser receipt directory');
const receipts = names
  .filter((name) => name.endsWith('-receipt.json'))
  .map((name) => readEvidence(join(directory, name)));
const artifact = identity();
const current = metadata();
const context = sourceContext();
for (const receipt of receipts)
  if (
    receipt.runner?.run !== current.run ||
    receipt.runner?.attempt !== current.attempt ||
    JSON.stringify(receipt.sourceContext) !== JSON.stringify(context)
  )
    throw new Error('Mixed run or attempt browser receipts');
for (const receipt of receipts)
  if (JSON.stringify(receipt.selectionPlan) !== JSON.stringify(plan))
    throw new Error('Wrong change-risk selection');
for (const p of requiredPartitions(profiles).filter((p) => ['ordinary', 'admin'].includes(p.suite)))
  validateNativeSelection(
    inventory[p.suite].filter((r) => r.project === p.project),
    p.suite,
    p.project,
  );

const result = joinReceipts(inventory, receipts, artifact, requiredPartitions(profiles));
writeJson(join(directory, 'browser-acceptance.json'), {
  schema: 2,
  policy: policyHash,
  profiles,
  selectionPlan: plan,
  status: 'passed',
  identity: artifact,
  runner: current,
  sourceContext: context,
  receipts,
  inventory,
  ...result,
  timings: { aggregationMs: performance.now() - aggregationStarted },
});
console.log(
  `Accepted ${result.selected} independently discovered entries across ${result.partitions} partitions`,
);
