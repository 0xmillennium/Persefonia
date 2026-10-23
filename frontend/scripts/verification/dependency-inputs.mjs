/** Hash effective dependency inputs without printing configuration values or credentials. */
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
let effective;
try {
  effective = execFileSync('npm', ['config', 'list', '--json'], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
} catch {
  throw new Error(
    'Cannot fingerprint effective npm configuration; dependency reuse is unavailable',
  );
}
const runtime = execFileSync('npm', ['--version'], { encoding: 'utf8' }).trim();
const environment = Object.fromEntries(
  Object.entries(process.env)
    .filter(([key]) => /^(npm_config_|NPM_CONFIG_|NODE_OPTIONS$|NODE_ENV$|PATH$)/.test(key))
    .sort(),
);
console.log(
  createHash('sha256')
    .update(
      JSON.stringify({
        node: process.version,
        executable: process.execPath,
        platform: process.platform,
        arch: process.arch,
        npm: runtime,
        effective,
        environment,
      }),
    )
    .digest('hex'),
);
