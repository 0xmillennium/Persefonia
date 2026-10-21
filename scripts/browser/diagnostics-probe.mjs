// A disposable intentional failure proves capture preserves the primary assertion and finalizes a trace.
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { resolve, join, sep } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const directory = resolve(process.argv[2] ?? '');
if (!directory.startsWith(resolve(root, '.local/work/ci-reliability') + sep))
  throw new Error('Probe requires an owned CI reliability workspace');
mkdirSync(directory);
writeFileSync(join(directory, 'probe.spec.ts'), `import {test,expect} from ${JSON.stringify(join(root, 'frontend/e2e/support/test'))};
test('intentional paused-clock primary failure', async ({page}) => {
  await page.setContent('<p>probe</p>');
  await page.clock.install();
  await page.clock.pauseAt(Date.now()+100);
  expect('primary diagnostic control').toBe('intentional failure');
});
test('intentional failure with stalled diagnostics', async ({page,context}) => {
  await page.setContent('<p>probe</p>');
  await page.clock.install();
  await page.clock.pauseAt(Date.now()+100);
  page.screenshot = () => new Promise(() => {});
  context.tracing.stop = () => new Promise(() => {});
  expect('primary diagnostic control').toBe('intentional failure');
});`);
writeFileSync(join(directory, 'config.mjs'), `export default { testDir:${JSON.stringify(directory)},testMatch:'probe.spec.ts',
  retries:0,workers:1,timeout:10000,outputDir:${JSON.stringify(join(directory, 'output'))},
  use:{browserName:'chromium',channel:'chromium',trace:'off',screenshot:'off'},
  reporter:[['json',{outputFile:${JSON.stringify(join(directory, 'results.json'))}}]]};`);
const started = performance.now();
const run = spawnSync(process.execPath, [join(root, 'frontend/node_modules/@playwright/test/cli.js'), 'test',
  '--config', join(directory, 'config.mjs')], { cwd: root, encoding:'utf8', timeout:45000 });
writeFileSync(join(directory, 'process.log'), run.stdout + run.stderr);
const report = JSON.parse(readFileSync(join(directory, 'results.json'), 'utf8'));
const results = report.suites.flatMap(suite => suite.specs.flatMap(spec => spec.tests.flatMap(test => test.results)));
if (run.status !== 1 || results.length !== 2 || results.some(result => result.status !== 'failed' || result.retry !== 0 ||
    !result.error.message.includes('primary diagnostic control') || result.errors.length !== 1))
  throw new Error('Primary failure was masked');
const diagnostic = results[1].attachments.find(attachment => attachment.name === 'diagnostic-errors');
if (!diagnostic) throw new Error('Missing explicit diagnostic failure');
const failure = JSON.parse(diagnostic.body ? Buffer.from(diagnostic.body, 'base64').toString('utf8') : readFileSync(diagnostic.path, 'utf8'));
if (!failure.diagnosticErrors.some(error => error.includes('screenshot deadline')) ||
    !failure.diagnosticErrors.some(error => error.includes('trace finalization deadline')))
  throw new Error('Stalled diagnostics were not bounded independently');
const validation = spawnSync('python3', ['-B', join(root, 'scripts/ci/validate-traces.py'),
  join(directory, 'output'), join(directory, 'trace-validation.json')], { cwd:root, encoding:'utf8' });
if (validation.status !== 0) throw new Error('Trace validation command failed');
const traces = JSON.parse(readFileSync(join(directory, 'trace-validation.json'), 'utf8')).traces;
if (traces.length !== 1 || !traces[0].readable) throw new Error('Intentional failure trace is unreadable');
writeFileSync(join(directory, 'receipt.json'), JSON.stringify({primaryPreserved:true, diagnosticFailureBounded: true, diagnosticErrors: failure.diagnosticErrors, traces,
  durationMs: performance.now()-started, retries:0})+'\n');
console.log('Both primary failures preserved; normal trace readable; stalled capture bounded and classified');
