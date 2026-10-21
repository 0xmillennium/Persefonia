import { expect, test } from '../support/test';
import { readFileSync } from 'node:fs';
import { openHomepage } from '../support/homepage';

/** Exercise the actual dedicated entry under the application's existing CSP. */
test('the isolated diagram bundle renders flowcharts and sequence diagrams', async ({ page }) => {
  await openHomepage(page);
  const manifest = JSON.parse(
    readFileSync(
      new URL('../../../app/build/generated/vite/static/assets/.vite/manifest.json', import.meta.url),
      'utf8',
    ),
  );
  const entry = `/assets/${manifest['src/mermaid-loader.ts'].file}`;
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.evaluate(async (entry) => {
    for (const code of [
      'graph TD; A[Client] --> B[Server]',
      'sequenceDiagram\nClient->>Server: Request\nServer-->>Client: Response',
    ]) {
      const diagram = document.createElement('pre');
      diagram.className = 'mermaid';
      diagram.textContent = code;
      document.body.append(diagram);
    }
    await import(entry);
  }, entry);
  await expect(page.locator('.mermaid svg')).toHaveCount(2);
  await expect(page.locator('.mermaid').first()).toContainText('Client');
  await expect(page.locator('.mermaid').nth(1)).toContainText('Response');
  expect(errors).toEqual([]);
});
