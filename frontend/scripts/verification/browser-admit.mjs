import { readEvidence } from './browser-runtime.mjs';
import { validateAcceptance } from './browser-contract.mjs';

const [path, sourceSha, artifactSha256] = process.argv.slice(2);
if (!path || !sourceSha || !artifactSha256 || process.argv.length !== 5)
  throw new Error('Expected acceptance path, source SHA and candidate digest');
const result = validateAcceptance(readEvidence(path), {
  sourceSha,
  artifactSha256,
});
console.log(
  `Validated ${result.selected} native browser identities across ${result.partitions} partitions`,
);
