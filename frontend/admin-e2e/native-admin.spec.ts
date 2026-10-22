import { test, expect } from '../e2e/support/test';
import { randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';

async function assertDocumentReferences(page: Page) {
  expect(
    await page.evaluate(() => {
      const ids = [...document.querySelectorAll('[id]')].map((node) => node.id);
      return ids.filter((id, index) => ids.indexOf(id) !== index);
    }),
  ).toEqual([]);
  for (const element of await page.locator('[aria-describedby]').all()) {
    for (const id of (await element.getAttribute('aria-describedby'))!.split(/\s+/)) {
      await expect(page.locator(`[id="${id}"]`)).toHaveCount(1);
    }
  }
  for (const link of await page.locator('.field-errors a[href^="#"]').all()) {
    const field = page.locator((await link.getAttribute('href'))!);
    await expect(field).toHaveCount(1);
    await link.click();
    await expect(field).toBeFocused();
  }
}

test('populated content tags retain distinct labels, archived selection and native submission', async ({
  page,
}, info) => {
  const identity = randomUUID().slice(0, 8);
  const tags: { name: string; id: string; edit: string }[] = [];
  for (const suffix of ['first', 'second', 'archived']) {
    const name = `Synthetic ${identity} ${suffix}`;
    await page.goto('/admin/tags/new');
    await page.locator('#name').fill(name);
    await page.locator('#slug').fill(`synthetic-${identity}-${suffix}`);
    await page.getByRole('button', { name: 'Create tag', exact: true }).click();
    await expect(page).toHaveURL(/\/admin\/tags\/.+\/edit/);
    tags.push({ name, id: new URL(page.url()).pathname.split('/')[3]!, edit: page.url() });
  }
  await page.goto('/admin/content/new');
  await page.locator('#type').selectOption('ARTICLE');
  await page.locator('#language').selectOption('EN');
  await page.locator('#visibility').selectOption('PRIVATE');
  await page.getByRole('button', { name: 'Create draft', exact: true }).click();
  await expect(page).toHaveURL(/\/admin\/content\/.+\/edit/);
  const edit = page.url();
  for (const tag of [tags[0]!, tags[2]!]) {
    await page.getByLabel(tag.name, { exact: true }).check();
  }
  await page.getByRole('button', { name: 'Save tags', exact: true }).click();
  await expect(page).toHaveURL(/tagsSaved/);
  await page.goto(tags[2]!.edit);
  await page.getByRole('button', { name: 'Archive', exact: true }).click();
  await page.goto(edit);
  await assertDocumentReferences(page);
  const group = page.getByRole('group', { name: 'Tags', exact: true });
  await expect(group).toHaveCount(1);
  const first = page.getByLabel(tags[0]!.name, { exact: true });
  const second = page.getByLabel(tags[1]!.name, { exact: true });
  const archived = page.getByLabel(`${tags[2]!.name} ARCHIVED`, { exact: true });
  await expect(first).toBeChecked();
  await expect(second).not.toBeChecked();
  await expect(archived).toBeChecked();
  for (const [control, tag] of [
    [first, tags[0]!],
    [second, tags[1]!],
    [archived, tags[2]!],
  ] as const) {
    await expect(control).toHaveValue(tag.id);
  }
  await page.getByText(tags[1]!.name, { exact: true }).click();
  await expect(second).toBeChecked();
  await expect(first).toBeChecked();
  await expect(archived).toBeChecked();
  await page.getByRole('button', { name: 'Save tags', exact: true }).click();
  await expect(page.getByLabel(tags[1]!.name, { exact: true })).toBeChecked();
  await expect(page.getByLabel(`${tags[2]!.name} ARCHIVED`, { exact: true })).toBeChecked();
  await assertDocumentReferences(page);
  if (process.env.PERSEFONIA_ADMIN_EVIDENCE_DIR) {
    await page.getByRole('group', { name: 'Tags', exact: true }).screenshot({
      path: `${process.env.PERSEFONIA_ADMIN_EVIDENCE_DIR}/${info.project.name}-content-tags.png`,
    });
  }
});

test('series validation links focus the correct language, title and slug controls', async ({
  page,
}, info) => {
  for (const [language, title, slug, field] of [
    ['INVALID', 'Synthetic series', 'synthetic-series', 'language'],
    ['EN', ' ', 'synthetic-series', 'title'],
    ['EN', 'Synthetic series', 'bad slug', 'slug'],
  ]) {
    await page.goto('/admin/series/new');
    await page.locator('#title').fill(title!);
    await page.locator('#slug').fill(slug!);
    if (language === 'INVALID') {
      // Submit a real tampered form through native navigation, keeping its OWNER session and CSRF.
      await page.locator('#language').evaluate((select) => {
        select.appendChild(new Option('Invalid synthetic language', 'INVALID', true, true));
      });
    }
    await page.getByRole('button', { name: 'Create series', exact: true }).click();
    const control = page.locator(`#${field}`);
    await expect(page.locator('.field-errors')).toBeFocused();
    await expect(page.locator(`.field-errors a[href="#${field}"]`)).toHaveCount(1);
    await expect(control).toHaveAttribute('aria-invalid', 'true');
    await expect(control).toHaveAttribute('aria-describedby', `error-${field}`);
    await assertDocumentReferences(page);
    await expect(control).toBeFocused();
    if (process.env.PERSEFONIA_ADMIN_EVIDENCE_DIR && field === 'language') {
      await page.screenshot({
        path: `${process.env.PERSEFONIA_ADMIN_EVIDENCE_DIR}/${info.project.name}-series-language.png`,
      });
    }
  }
});

test('adjacent content and tag validation summaries keep unique focusable destinations', async ({
  page,
}) => {
  for (const [path, button] of [
    ['/admin/content/new', 'Create draft'],
    ['/admin/tags/new', 'Create tag'],
  ]) {
    await page.goto(path!);
    await page.getByRole('button', { name: button!, exact: true }).click();
    await expect(page.locator('.field-errors')).toBeVisible();
    await assertDocumentReferences(page);
  }
});

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
