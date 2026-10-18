import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { lazyPhysicalIdentity } from './lazy';
const fixture = vi.hoisted(() => ({ active: vi.fn(), dispose: vi.fn(), mount: vi.fn() }));
vi.mock('./physical', () => ({ physicalIdentity: fixture.mount }));
beforeEach(() => {
  vi.clearAllMocks();
  fixture.mount.mockReturnValue(fixture);
  vi.stubGlobal('matchMedia', () => ({ matches: false }));
});
afterEach(() => vi.unstubAllGlobals());
it('does not initialize physics until nearby, then applies the latest active state', async () => {
  const changed = vi.fn();
  const owner = lazyPhysicalIdentity({} as SVGSVGElement, changed);
  expect(fixture.mount).not.toHaveBeenCalled();
  owner.prepare();
  owner.active(true);
  owner.active(false);
  await vi.dynamicImportSettled();
  expect(fixture.mount).toHaveBeenCalledOnce();
  expect(fixture.active).toHaveBeenLastCalledWith(false);
  owner.dispose();
  owner.prepare();
  expect(fixture.dispose).toHaveBeenCalledOnce();
});
it('a late import cannot initialize a disposed page', async () => {
  const owner = lazyPhysicalIdentity({} as SVGSVGElement, vi.fn());
  owner.prepare();
  owner.dispose();
  await vi.dynamicImportSettled();
  expect(fixture.mount).not.toHaveBeenCalled();
});
it('reduced motion retains the static identity without constructing physics', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true }));
  const owner = lazyPhysicalIdentity({} as SVGSVGElement, vi.fn());
  owner.prepare();
  owner.active(true);
  await vi.dynamicImportSettled();
  expect(fixture.mount).not.toHaveBeenCalled();
  owner.dispose();
});
