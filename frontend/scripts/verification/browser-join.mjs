import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { reportTests, joinReceipts } from './browser-contract.mjs';
import {
  repository,
  identity,
  metadata,
  sourceContext,
  playwright,
  writeJson,
} from './browser-runtime.mjs';

const aggregationStarted = performance.now();
const directory = resolve(process.argv[2] ?? join(repository, 'app/build/browser/verification'));
const inventory = {};
for (const suite of ['chromium', 'cross']) {
  const path = join(directory, `${suite}-complete-discovery.json`);
  const discovery = playwright(suite, [], path, true);
  if (discovery.exitCode !== 0) throw new Error(`Independent ${suite} discovery failed`);
  inventory[suite] = reportTests(JSON.parse(readFileSync(path, 'utf8')));
}
const receipts = readdirSync(directory)
  .filter((name) => name.endsWith('-receipt.json'))
  .map((name) => JSON.parse(readFileSync(join(directory, name), 'utf8')));
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
const result = joinReceipts(inventory, receipts, artifact);
writeJson(join(directory, 'browser-acceptance.json'), {
  schema: 1,
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
