/** One discovery, one execution, one immutable app/DB; first attempts only. */
import { readFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { environment } from './environment.mjs';
import {
  repository,
  identity,
  metadata,
  sourceContext,
  writeJson,
  playwright,
  verificationPlan,
} from './browser-runtime.mjs';
import {
  policyHash,
  requiredPartitions,
  validateNativeSelection,
  selectionPolicy,
} from './selection-policy.mjs';
import {
  reportTests,
  sameInventory,
  validateOutcomes,
  digest,
  partitionKey,
} from './browser-contract.mjs';
if (process.argv.slice(2).join(' ') === '--preflight') {
  console.log(
    `Fixed ordinary native inventory: ${selectionPolicy.native.reduce((n, p) => n + p.cases.length, 0)} cases`,
  );
} else {
  if (process.argv.length !== 2)
    throw new Error('Mandatory native verification cannot use filters');
  const output = resolve(
    process.env.PERSEFONIA_RECEIPT_DIR ?? join(repository, 'app/build/browser/verification'),
  );
  const plan = verificationPlan(),
    source = sourceContext(),
    artifact = identity(true),
    runner = metadata();
  const discoveryPath = join(output, 'native-discovery.json'),
    resultsPath = join(output, 'native-results.json');
  for (const p of requiredPartitions())
    rmSync(join(output, `${partitionKey(p)}-receipt.json`), { force: true });
  for (const path of [discoveryPath, resultsPath]) rmSync(path, { force: true });
  const discovery = await playwright('ordinary', [], discoveryPath, true);
  if (discovery.exitCode !== 0) throw new Error('Native discovery failed');
  const planned = reportTests(JSON.parse(readFileSync(discoveryPath, 'utf8')));
  for (const p of requiredPartitions())
    validateNativeSelection(
      planned.filter((r) => r.project === p.project),
      'ordinary',
      p.project,
    );
  const execution = await playwright('ordinary', [], resultsPath);
  let firstFailure = execution.exitCode;
  const outcomes = reportTests(JSON.parse(readFileSync(resultsPath, 'utf8'))).map((row) => ({
    ...row,
    results: row.results.map(({ status, duration, retry, startTime }) => ({
      status,
      duration,
      retry,
      startTime,
    })),
  }));
  sameInventory(
    planned.map((r) => r.id),
    outcomes.map((r) => r.id),
  );
  const unchanged = JSON.stringify(sourceContext()) === JSON.stringify(source);
  // Each engine receipt is projected from this one actual first execution, never rerun.
  for (const partition of requiredPartitions()) {
    const selected = planned.filter((r) => r.project === partition.project).map((r) => r.id);
    const rows = outcomes.filter((r) => r.project === partition.project);
    const receipt = {
      schema: 3,
      policy: policyHash,
      selectionPlan: plan,
      partition,
      identity: artifact,
      sourceContext: source,
      runner,
      selected,
      selectionHash: digest([...selected].sort()),
      outcomes: rows,
      status: 'failed',
      exitCode: execution.exitCode,
      timings: { discovery, execution },
      errors: [],
    };
    try {
      if (execution.exitCode !== 0 || !unchanged)
        throw new Error('Native process failed or source changed');
      const project = selectionPolicy.native.find((p) => p.project === partition.project);
      receipt.environment = environment([project.engine]);
      validateNativeSelection(rows, 'ordinary', partition.project);
      receipt.counts = validateOutcomes(rows);
      receipt.status = 'passed';
    } catch (error) {
      receipt.errors.push(String(error));
      receipt.exitCode ||= 1;
      firstFailure ||= 1;
    }
    writeJson(join(output, `${partitionKey(partition)}-receipt.json`), receipt);
  }
  process.exitCode = firstFailure;
}
