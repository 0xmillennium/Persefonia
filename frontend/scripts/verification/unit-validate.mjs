import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { frontend, verificationPlan } from './browser-runtime.mjs';
import {
  validateUnitAttempts,
  validateUnitExecution,
  validateUnitPhases,
  unitEvidenceInventory,
} from './unit-contract.mjs';

import { policyHash } from './selection-policy.mjs';
const receipt = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const native = receipt.native;
const plan = verificationPlan();
const physicsProfile = 'ordinary';
if (
  receipt.unit.profile !== 'ordinary' ||
  receipt.unit.schema !== 2 ||
  receipt.unit.policy !== policyHash ||
  receipt.unit.physicsProfile !== physicsProfile
)
  throw new Error('Wrong frontend policy/profile');
process.env.PERSEFONIA_PHYSICS_PROFILE = physicsProfile;
const discovered = JSON.parse(
  execFileSync(
    process.execPath,
    [
      join(frontend, 'node_modules/vitest/vitest.mjs'),
      'list',
      '--no-static-parse',
      '--json',
      '--allowOnly=false',
    ],
    { cwd: frontend, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 },
  ),
);
if (
  JSON.stringify(unitEvidenceInventory(discovered)) !==
  JSON.stringify(unitEvidenceInventory(native.discovery))
)
  throw new Error('Frontend evidence differs from independent source discovery');
const cases = validateUnitExecution(
  native.discovery,
  native.report,
  native.scalar,
  native.live,
  physicsProfile,
);
validateUnitAttempts(cases, native.attempts);
validateUnitPhases(cases, native.phases);
const summaries = cases.map(({ file, fullName, status, duration }) => ({
  file,
  fullName,
  status,
  duration,
}));
if (
  JSON.stringify(unitEvidenceInventory(summaries)) !==
  JSON.stringify(unitEvidenceInventory(receipt.unit.cases))
)
  throw new Error('Unit receipt/native evidence mismatch');
console.log(`Independently accepted ${cases.length} unit cases and both stress inventories`);
