import { readEvidence } from './browser-runtime.mjs';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { reportTests, joinReceipts, validateAcceptance } from './browser-contract.mjs';
import { playwright } from './browser-runtime.mjs';

import { requiredPartitions, nativeProfiles } from './selection-policy.mjs';
import { riskPlan } from './browser-runtime.mjs';
const receipt = readEvidence(process.argv[2]);
validateAcceptance(receipt, receipt.identity);
const directory = mkdtempSync(join(tmpdir(), 'persefonia-browser-validation-'));
try {
  const inventory = {};
  for (const suite of nativeProfiles(riskPlan())) {
    const path = join(directory, `${suite}.json`);
    if (playwright(suite, [], path, true).exitCode !== 0)
      throw new Error('Source browser discovery failed');
    inventory[suite] = reportTests(readEvidence(path));
  }
  const result = joinReceipts(
    inventory,
    receipt.receipts,
    receipt.identity,
    requiredPartitions(nativeProfiles(riskPlan())),
  );
  if (result.selected !== receipt.selected || result.partitions !== receipt.partitions)
    throw new Error('Browser acceptance inventory mismatch');
  console.log(`Independently accepted ${result.selected} browser identities`);
} finally {
  rmSync(directory, { recursive: true, force: true });
}
