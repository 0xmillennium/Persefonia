import { expect, it } from 'vitest';
import { filmTier, loopTier } from '../delivery';

it('uses the qualified smaller loop for portrait phones while keeping rich wide delivery', () => {
  expect(loopTier(390, 850)).toBe(1280);
  expect(loopTier(430, 950, 1920)).toBe(1280);
  expect(loopTier(1440, 900)).toBe(1920);
  expect(loopTier(850, 390)).toBe(1280);
});

it('accounts for the portrait cover crop and avoids resizing churn near a tier boundary', () => {
  expect(filmTier(390, 850)).toBe(1600);
  expect(filmTier(960, 540)).toBe(960);
  expect(filmTier(1440, 900)).toBe(1600);
  expect(filmTier(1100, 600, 960)).toBe(960);
  expect(filmTier(1100, 600, 1600)).toBe(1600);
  expect(filmTier(900, 500, 1600)).toBe(960);
});
