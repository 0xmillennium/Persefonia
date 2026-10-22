import { createHash } from 'node:crypto';
import { expect, test } from '@playwright/test';

// This profile never receives production credentials. Contexts and synthetic accounts are per case.
test.beforeEach(async ({ context }, info) => {
  const namespace = createHash('sha256').update(info.testId).digest('hex').slice(0, 24);
  await context.setExtraHTTPHeaders({ 'X-Persefonia-Preview-Case': namespace });
  const response = await context.request.post('/__admin_preview/session', {
    headers: { 'X-Persefonia-Preview': 'synthetic' },
  });
  expect(response.status()).toBe(204);
});
test.afterEach(async ({ page }, info) => {
  if (info.status !== info.expectedStatus) {
    try {
      await page.screenshot({
        path: info.outputPath('admin-failure.png'),
        timeout: 4000,
        animations: 'disabled',
      });
    } catch {
      console.error('Admin screenshot unavailable; original test failure retained');
    }
  }
});

test('isolated real admin validation preserves private responses and summary focus', async ({
  page,
}) => {
  const response = await page.goto('/admin/projects/new');
  expect(response?.status()).toBe(200);
  expect(response?.headers()['cache-control']).toContain('no-store');
  await page.locator('#enEnabled').check();
  await page.getByRole('button', { name: 'Save project', exact: true }).click();
  const summary = page.locator('.field-errors');
  await expect(summary).toBeVisible();
  await expect(summary).toBeFocused();
  expect(await summary.locator('a').count()).toBeGreaterThan(0);
  for (const link of await summary.locator('a').all()) {
    const field = page.locator((await link.getAttribute('href'))!);
    await expect(field).toHaveCount(1);
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    const description = await field.getAttribute('aria-describedby');
    await expect(page.locator(`#${description}`)).toHaveCount(1);
    await link.click();
    await expect(field).toBeFocused();
  }
});

test('isolated real admin submission requires OWNER and CSRF', async ({ page, context }) => {
  await page.goto('/admin/projects/new');
  const fields = {
    status: 'EXPERIMENT',
    visibility: 'PRIVATE',
    enEnabled: 'true',
    enSlug: 'synthetic-permission-witness',
    enTitle: 'Synthetic permission witness',
    enSummary: 'Disposable test content.',
  };
  expect((await context.request.post('/admin/projects', { form: fields })).status()).toBe(403);
  expect(
    (
      await context.request.post('/__admin_preview/session?role=editor', {
        headers: { 'X-Persefonia-Preview': 'synthetic' },
      })
    ).status(),
  ).toBe(204);
  await page.goto('/admin/projects/new');
  const csrf = await page.locator('main input[name="_csrf"]').inputValue();
  expect(csrf).not.toBe('');
  expect(
    (await context.request.post('/admin/projects', { form: { ...fields, _csrf: csrf } })).status(),
  ).toBe(403);
});
