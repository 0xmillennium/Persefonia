import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { consumeNavigationMarker, rememberNavigation } from './marker';
let values: Map<string, string>;
beforeEach(() => {
  values = new Map();
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  });
});
afterEach(() => vi.unstubAllGlobals());
it('restores once only for the intended fresh navigation', () => {
  rememberNavigation('https://example.test/a');
  expect(consumeNavigationMarker('https://example.test/a', 'navigate')).toBe(true);
  expect(consumeNavigationMarker('https://example.test/a', 'navigate')).toBe(false);
});
it.each(['wrong-url', 'history', 'expired', 'future', 'invalid'])(
  'rejects %s markers without retaining them',
  (mode) => {
    const now = Date.now();
    values.set(
      'persefonia-navigation-cover',
      mode === 'invalid'
        ? 'bad'
        : JSON.stringify({
            url: 'https://example.test/a',
            time: mode === 'expired' ? now - 60001 : mode === 'future' ? now + 1 : now,
          }),
    );
    expect(
      consumeNavigationMarker(
        mode === 'wrong-url' ? 'https://example.test/b' : 'https://example.test/a',
        mode === 'history' ? 'back_forward' : 'navigate',
        now,
      ),
    ).toBe(false);
    expect(values.size).toBe(0);
  },
);
it('keeps navigation usable when storage is denied', () => {
  vi.stubGlobal('sessionStorage', {
    getItem() {
      throw Error('denied');
    },
    setItem() {
      throw Error('denied');
    },
  });
  expect(rememberNavigation('/a')).toBe(false);
  expect(consumeNavigationMarker('/a', 'navigate')).toBe(false);
});
