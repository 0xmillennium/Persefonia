import { environment } from './environment.mjs';
import { readFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import {
  partitionKey,
  reportTests,
  sameInventory,
  validateOutcomes,
  digest,
  skipRules,
} from './browser-contract.mjs';
import {
  repository,
  identity,
  metadata,
  sourceContext,
  writeJson,
  playwright,
  riskPlan,
} from './browser-runtime.mjs';
import {
  policyHash,
  nativeProfiles,
  requiredPartitions,
  validateNativeSelection,
} from './selection-policy.mjs';

// Mandatory qualification accepts only the source policy's profile/context identity.
const [suite, flag, project, ...extra] = process.argv.slice(2);
const shard = process.env.PERSEFONIA_BROWSER_SHARD ?? '1/1';
const plan = riskPlan();
const partition = requiredPartitions(nativeProfiles(plan)).find(
  (p) => p.suite === suite && p.project === project && p.shard === shard,
);
if (flag !== '--project' || extra.length || !partition)
  throw new Error('Unknown or narrowed mandatory browser partition');
const key = partitionKey(partition);
const output = resolve(
  process.env.PERSEFONIA_RECEIPT_DIR ?? join(repository, 'app/build/browser/verification'),
);
const discoveryPath = join(output, `${key}-discovery.json`),
  resultsPath = join(output, `${key}-results.json`);
const receiptPath = join(output, `${key}-receipt.json`);
for (const path of [discoveryPath, resultsPath, receiptPath]) rmSync(path, { force: true });
const receipt = {
  schema: 2,
  policy: policyHash,
  selectionPlan: plan,
  partition,
  status: 'failed',
  exitCode: 1,
  selected: [],
  outcomes: [],
  timings: {},
  errors: [],
};
try {
  receipt.identity = identity(true);
  receipt.sourceContext = sourceContext();
  receipt.runner = metadata();
  receipt.environment = environment([
    project.startsWith('firefox')
      ? 'firefox'
      : project.startsWith('webkit')
        ? 'webkit'
        : 'chromium',
  ]);
  const selection = [...(project === '*' ? [] : [`--project=${project}`]), `--shard=${shard}`];
  const discovery = playwright(suite, selection, discoveryPath, true);
  receipt.timings.discovery = discovery;
  if (discovery.exitCode !== 0) throw new Error('Discovery failed');
  const planned = reportTests(JSON.parse(readFileSync(discoveryPath, 'utf8')));
  if (['ordinary', 'admin'].includes(suite)) validateNativeSelection(planned, suite, project);
  receipt.selected = planned.map((r) => r.id);
  receipt.selectionHash = digest([...receipt.selected].sort());
  const execution = playwright(suite, selection, resultsPath);
  receipt.timings.execution = execution;
  receipt.exitCode = execution.exitCode;
  const started = performance.now();
  const report = JSON.parse(readFileSync(resultsPath, 'utf8'));
  receipt.outcomes = reportTests(report).map((row) => ({
    ...row,
    results: row.results.map(({ status, duration, retry, startTime }) => ({
      status,
      duration,
      retry,
      startTime,
    })),
  }));
  sameInventory(
    receipt.selected,
    receipt.outcomes.map((r) => r.id),
  );
  receipt.counts = validateOutcomes(receipt.outcomes, skipRules(suite));
  if (execution.exitCode !== 0 || report.stats.unexpected || report.stats.flaky)
    throw new Error('Browser process failed');
  receipt.timings.reconciliationMs = performance.now() - started;
  if (JSON.stringify(sourceContext()) !== JSON.stringify(receipt.sourceContext))
    throw new Error('Source changed during browser verification');
  receipt.status = 'passed';
} catch (error) {
  receipt.errors.push(String(error));
  receipt.exitCode = receipt.exitCode || 1;
} finally {
  const timingFiles = [
    process.env.PERSEFONIA_TIMINGS_FILE,
    process.env.PERSEFONIA_NPM_TIMINGS_FILE,
  ].filter(Boolean);
  if (timingFiles.length) {
    try {
      receipt.timings.setup = timingFiles.flatMap((timingFile) =>
        readFileSync(resolve(repository, timingFile), 'utf8')
          .trim()
          .split('\n')
          .filter(Boolean)
          .map((line) => JSON.parse(line)),
      );
    } catch (error) {
      receipt.errors.push(`Setup timing receipt failed: ${error}`);
      receipt.status = 'failed';
    }
  }
  writeJson(receiptPath, receipt);
  console.log(`Browser receipt: ${receiptPath} (${receipt.status})`);
}
process.exitCode = receipt.status === 'passed' ? 0 : 1;
