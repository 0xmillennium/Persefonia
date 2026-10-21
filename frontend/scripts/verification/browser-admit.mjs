import { readFileSync } from 'node:fs';
import { validateAcceptance } from './browser-contract.mjs';

const [path, sourceSha, artifactSha256] = process.argv.slice(2);
if (!path || !sourceSha || !artifactSha256 || process.argv.length !== 5)
  throw new Error('Expected acceptance path, source SHA and candidate digest');
const result = validateAcceptance(JSON.parse(readFileSync(path, 'utf8')), {
  sourceSha,
  artifactSha256,
});
console.log(
  `Validated ${result.selected} native browser identities across ${result.partitions} partitions`,
);
