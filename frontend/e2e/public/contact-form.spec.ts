import { expect, test } from '../support/test';

test('synthetic admin authentication is absent from the production application artifact', async ({
  request,
}) => {
  expect((await request.get('/__admin_preview/session')).status()).toBe(404);
  const response = await request.post('/__admin_preview/session', {
    headers: { 'X-Persefonia-Preview': 'synthetic' },
  });
  expect([403, 404]).toContain(response.status());
  expect((await request.get('/admin', { maxRedirects: 0 })).status()).toBe(403);
});

test('contact fields use a readable native layout with full-width labels and controls', async ({
  page,
}) => {
  await page.goto('/contact');
  const form = page.locator('main form');
  const box = (await form.boundingBox())!;
  for (const id of ['senderName', 'senderEmail', 'subject', 'body']) {
    const control = page.locator(`#${id}`);
    const field = (await control.boundingBox())!;
    const label = (await page.locator(`label[for="${id}"]`).boundingBox())!;
    expect(label.y + label.height).toBeLessThanOrEqual(field.y);
    expect(field.width).toBeGreaterThanOrEqual(Math.min(box.width - 2, 480));
    expect(field.height).toBeGreaterThanOrEqual(44);
  }
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth - innerWidth),
  ).toBeLessThanOrEqual(1);
  await page.locator('#senderName').focus();
  await expect(page.locator('#senderName')).toBeFocused();
  expect(
    await page.locator('#senderName').evaluate((n) => getComputedStyle(n).outlineStyle),
  ).not.toBe('none');
});
