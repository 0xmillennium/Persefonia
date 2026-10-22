import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
const bytes = readFileSync(new URL('./selection-policy.json', import.meta.url));
export const selectionPolicy = JSON.parse(bytes);
if (
  selectionPolicy.schema !== 1 ||
  selectionPolicy.version !== 'ordinary-native-v1' ||
  selectionPolicy.receiptSchema !== 2
)
  throw new Error('Unknown selection policy schema/version');
export const policyHash = createHash('sha256').update(bytes).digest('hex');
export const nativeProfiles = (plan) =>
  plan.required.filter(
    (name) =>
      name === 'ordinary' || ['native', 'native-admin'].includes(selectionPolicy.deep[name]?.kind),
  );
export function requiredPartitions(profiles = ['ordinary']) {
  return profiles.flatMap((suite) => {
    const projects =
      suite === 'ordinary'
        ? selectionPolicy.native.map((p) => p.project)
        : selectionPolicy.deep[suite]?.projects;
    if (!projects) throw new Error('Unknown native profile');
    return (suite === 'admin' ? selectionPolicy.adminNative : selectionPolicy.native)
      .filter((p) => projects.includes(p.project))
      .map((p) => ({
        name: `${suite}-${p.name}`,
        suite,
        project: p.project,
        shard: '1/1',
      }));
  });
}
export function validateNativeSelection(rows, suite, project) {
  const wanted = (suite === 'admin' ? selectionPolicy.adminNative : selectionPolicy.native).find(
    (p) => p.project === project,
  )?.cases;
  const key = (r) => JSON.stringify([r.file, r.title]);
  if (
    !wanted ||
    rows.length !== wanted.length ||
    new Set(rows.map(key)).size !== rows.length ||
    !wanted.every((r) => rows.some((a) => a.project === project && key(a) === key(r)))
  )
    throw new Error('Source policy differs from independent native discovery');
}

export const validateOrdinarySelection = (rows, project) =>
  validateNativeSelection(rows, 'ordinary', project);
