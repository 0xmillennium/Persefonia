import { expect, it } from 'vitest';
import { build } from 'vite';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { assetBudgets } from './budgets';
it('allows a large optional chunk but rejects the same bytes in the eager homepage closure', async () => {
  const directory = await mkdtemp(`${tmpdir()}/entry-budget-`);
  const run = () =>
    build({
      configFile: false,
      root: directory,
      logLevel: 'silent',
      plugins: [assetBudgets()],
      build: { write: false, rollupOptions: { input: { home: `${directory}/home.ts` } } },
    });
  try {
    await writeFile(`${directory}/optional.ts`, `globalThis.payload = '${'x'.repeat(240_000)}';`);
    await writeFile(
      `${directory}/home.ts`,
      `document.addEventListener('click', () => import('./optional'));`,
    );
    await expect(run()).resolves.toBeDefined();
    await writeFile(`${directory}/home.ts`, `import './optional';`);
    await expect(run()).rejects.toThrow('eager JS budget exceeded');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
