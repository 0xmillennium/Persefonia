import { expect, it } from 'vitest';
import { storedPosition } from './history';

it('accepts same-viewport history and rejects corrupt or stale measurements', () => {
  expect(storedPosition({ y: 7824, width: 1440, height: 900 }, 1440, 900)).toBe(7824);
  for (const value of [
    null,
    {},
    { y: NaN },
    { y: -1, width: 1440, height: 900 },
    { y: 7824, width: 390, height: 850 },
    { y: 7824, width: 1440, height: 850 },
  ]) {
    expect(storedPosition(value, 1440, 900)).toBeNull();
  }
});
