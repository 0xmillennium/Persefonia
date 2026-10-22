import { test, expect } from '../e2e/support/test';
import { randomUUID } from 'node:crypto';

test.beforeEach(async ({ context }) => {
  const session = await context.request.post('/__admin_preview/session', {
    headers: { 'X-Persefonia-Preview': 'synthetic' },
  });
  expect(session.status()).toBe(204);
});

test('real authenticated admin route families share the shell and preserve private responses', async ({
  page,
}) => {
  const paths = [
    '/admin',
    '/admin/content',
    '/admin/content/new',
    '/admin/series',
    '/admin/series/new',
    '/admin/tags',
    '/admin/tags/new',
    '/admin/discovery/redirects',
    '/admin/profile',
    '/admin/cv',
    '/admin/projects',
    '/admin/projects/new',
    '/admin/media',
    '/admin/media/new',
    '/admin/contact',
    '/admin/analytics',
    '/admin/audit',
    '/admin/operations',
    '/admin/operations/recovery',
    '/admin/settings/site',
  ];
  for (const path of paths) {
    const response = await page.goto(path);
    expect(response?.status(), path).toBe(200);
    expect(response?.headers()['cache-control'], path).toContain('no-store');
    await expect(page.locator('body')).toHaveClass('admin-body');
    await expect(page.locator('head link[rel="stylesheet"]')).not.toHaveCount(0);
    await expect(page.locator('h1')).toHaveCount(1);
    await expect(page.locator('main#admin-content')).toHaveCount(1);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth - innerWidth),
      path,
    ).toBeLessThanOrEqual(1);
    expect(
      await page.locator('main').evaluate((main) => {
        const ids = [...main.querySelectorAll('[id]')].map((n) => n.id);
        return ids.filter((id, i) => ids.indexOf(id) !== i);
      }),
      path,
    ).toEqual([]);
    expect(
      await page
        .locator('main')
        .evaluate((main) =>
          [...main.querySelectorAll<HTMLInputElement>('input:not([type="hidden"]),textarea,select')]
            .filter(
              (n) =>
                !n.labels?.length &&
                !n.hasAttribute('aria-label') &&
                !n.hasAttribute('aria-labelledby'),
            )
            .map((n) => n.id || n.name),
        ),
      path,
    ).toEqual([]);
  }
});

test('invalid project fields have unique descriptions and working summary focus destinations', async ({
  page,
}) => {
  await page.goto('/admin/projects/new');
  await page.locator('#enEnabled').check();
  await page.getByRole('button', { name: 'Save project', exact: true }).click();
  const summary = page.locator('.field-errors');
  await expect(summary).toBeVisible();
  await expect(summary).toBeFocused();
  const links = summary.locator('a');
  expect(await links.count()).toBeGreaterThan(0);
  for (const link of await links.all()) {
    const hash = await link.getAttribute('href');
    const field = page.locator(hash!);
    await expect(field).toHaveCount(1);
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    const id = await field.getAttribute('aria-describedby');
    await expect(page.locator(`#${id}`)).toHaveCount(1);
    await link.click();
    await expect(field).toBeFocused();
  }
});

test('native admin form submission retains OWNER and CSRF enforcement', async ({
  page,
  context,
}) => {
  await page.goto('/admin/projects/new');
  const action = '/admin/projects';
  const fields = {
    status: 'EXPERIMENT',
    visibility: 'PRIVATE',
    enEnabled: 'true',
    enSlug: 'synthetic-permission-witness',
    enTitle: 'Synthetic permission witness',
    enSummary: 'Disposable local test content.',
  };
  expect((await context.request.post(action, { form: fields })).status()).toBe(403);
  expect(
    (
      await context.request.post('/__admin_preview/session?role=editor', {
        headers: { 'X-Persefonia-Preview': 'synthetic' },
      })
    ).status(),
  ).toBe(204);
  await page.goto('/admin/projects/new');
  const token = await page.locator('main input[name="_csrf"]').inputValue();
  expect((await context.request.post(action, { form: { ...fields, _csrf: token } })).status()).toBe(
    403,
  );
  await expect(page.locator('head meta[name="robots"]')).toHaveAttribute('content', /noindex/);
});

test('native authoring creates a synthetic tag and renders its real edit and list states', async ({
  page,
}, info) => {
  const identity = randomUUID().slice(0, 8);
  const name = `Preview ${info.project.name} ${identity}`;
  const slug = `preview-${info.project.name}-${identity}`;
  await page.goto('/admin/tags/new');
  await page.locator('#name').fill(name);
  await page.locator('#slug').fill(slug);
  await page.locator('#description').fill('Synthetic local browser qualification.');
  await page.getByRole('button', { name: 'Create tag', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/tags\/.+\/edit/);
  await expect(page.locator('#name')).toHaveValue(name);
  await expect(page.locator('#slug')).toHaveValue(slug);
  await page.goto('/admin/tags');
  await expect(page.getByText(name, { exact: true })).toBeVisible();
});

test('admin text enlargement keeps forms usable and tables scroll inside their own region', async ({
  page,
}) => {
  await page.setViewportSize({ width: 640, height: 900 });
  for (const path of [
    '/admin/projects',
    '/admin/projects/new',
    '/admin/profile',
    '/admin/settings/site',
  ]) {
    await page.goto(path);
    await page.evaluate(() => {
      document.documentElement.style.fontSize = '200%';
    });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth - innerWidth),
    ).toBeLessThanOrEqual(1);
    for (const field of await page
      .locator('main input:not([type="hidden"],[type="checkbox"]),main textarea,main select')
      .all()) {
      const box = await field.boundingBox();
      expect(box!.width).toBeGreaterThan(0);
      expect(box!.x + box!.width).toBeLessThanOrEqual(641);
    }
    await page.locator('main').focus();
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => document.activeElement?.closest('main') !== null)).toBe(true);
  }
});

test('real detail, revision, preview and operational templates preserve the bounded shell', async ({
  page,
}) => {
  // Requires fixtures/states.sql in the same disposable database as the public fixtures.
  const paths = [
    '/admin/series/20000000-0000-0000-0000-000000000001/edit',
    '/admin/contact/20000000-0000-0000-0000-000000000002',
    '/admin/operations/cache/20000000-0000-0000-0000-000000000003',
  ];
  for (const [list, pattern] of [
    ['/admin/projects', '/edit'],
    ['/admin/content', '/edit'],
    ['/admin/content', '/preview'],
    ['/admin/media', '/admin/media/'],
    ['/admin/audit', '/admin/audit/'],
  ]) {
    await page.goto(list!);
    const links = await page
      .locator('main a[href]')
      .evaluateAll((nodes) => nodes.map((n) => n.getAttribute('href')!));
    const path = links.find((href) => href.includes(pattern!) && !href.endsWith('/new'));
    expect(path, `${list} supplies a real detail state`).toBeTruthy();
    paths.push(path!);
    if (pattern === '/edit' && list === '/admin/content')
      paths.push(path!.replace('/edit', '/revisions'));
  }
  for (const path of paths) {
    const response = await page.goto(path);
    expect(response?.status(), path).toBe(200);
    expect(response?.headers()['cache-control'], path).toContain('no-store');
    await expect(page.locator('body')).toHaveClass('admin-body');
    await expect(page.locator('main#admin-content')).toHaveCount(1);
    if (path.startsWith('/admin/contact/')) {
      const message = page.getByRole('region', { name: 'Message body', exact: true });
      await expect(message).toHaveAttribute('tabindex', '0');
      await message.focus();
      await expect(message).toBeFocused();
    }
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth - innerWidth),
      path,
    ).toBeLessThanOrEqual(1);
  }
});
