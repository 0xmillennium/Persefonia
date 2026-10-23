import { expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { requiredPartitions } from './selection-policy.mjs';
it('admin diagnostics cannot enter the ordinary receipt inventory', () => {
  expect(() => requiredPartitions(['ordinary', 'admin'])).toThrow();
  expect(() => requiredPartitions(['admin'])).toThrow();
  const workflow = readFileSync(
    new URL('../../../.github/workflows/ci.yml', import.meta.url),
    'utf8',
  );
  expect(workflow).not.toContain('admin_risk');
  expect(workflow).not.toContain('admin-preview.py');
  const preview = readFileSync(
    new URL('../../../scripts/ci/admin-preview.py', import.meta.url),
    'utf8',
  );
  expect(preview).toContain('Admin native diagnostics are explicit local operations');
});
