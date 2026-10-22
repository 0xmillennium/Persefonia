/** One immutable DB/app lifecycle; distinct first-attempt receipts and fresh test contexts. */
import { spawnSync } from 'node:child_process';
import { riskPlan, frontend } from './browser-runtime.mjs';
import { nativeProfiles, requiredPartitions } from './selection-policy.mjs';
const plan = riskPlan();
if (process.argv.length > 2) {
  if (process.argv.slice(2).join(' ') !== '--preflight')
    throw new Error('Unauthorized native lifecycle filter');
  console.log(
    `Required native profiles: ${nativeProfiles(plan)
      .filter((p) => p !== 'admin')
      .join(', ')}`,
  );
} else {
  let firstFailure = 0;
  for (const p of requiredPartitions(nativeProfiles(plan).filter((p) => p !== 'admin'))) {
    const result = spawnSync(
      process.execPath,
      ['scripts/verification/browser-run.mjs', p.suite, '--project', p.project],
      {
        cwd: frontend,
        stdio: 'inherit',
        env: { ...process.env, PERSEFONIA_BROWSER_SHARD: p.shard },
      },
    );
    if (result.status !== 0) firstFailure ||= result.status ?? 1;
    // Do not allow later success to replace the first failure. Each engine still emits diagnostics.
  }
  process.exitCode = firstFailure;
}
