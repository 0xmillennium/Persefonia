import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
const bytes = readFileSync(new URL('./selection-policy.json', import.meta.url));
export const selectionPolicy = JSON.parse(bytes);
if (
  selectionPolicy.schema !== 2 ||
  selectionPolicy.version !== 'lean-native-v2' ||
  selectionPolicy.receiptSchema !== 3
)
  throw new Error('Unknown selection policy schema/version');
export const policyHash = createHash('sha256').update(bytes).digest('hex');
export function requiredPartitions(profiles = ['ordinary']) {
  if (JSON.stringify(profiles) !== '["ordinary"]')
    throw new Error('Diagnostics cannot qualify ordinary CI');
  return selectionPolicy.native.map((p) => ({
    name: `ordinary-${p.name}`,
    suite: 'ordinary',
    project: p.project,
    shard: '1/1',
  }));
}
export function validateNativeSelection(rows, suite, project) {
  const wanted =
    suite === 'ordinary' && selectionPolicy.native.find((p) => p.project === project)?.cases;
  const key = (r) => JSON.stringify([r.file, r.title]);
  if (
    !wanted ||
    rows.length !== wanted.length ||
    new Set(rows.map(key)).size !== rows.length ||
    !wanted.every((r) => rows.some((a) => a.project === project && key(a) === key(r)))
  )
    throw new Error('Source policy differs from independent native discovery');
}
