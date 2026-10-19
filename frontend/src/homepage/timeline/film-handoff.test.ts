import { expect, it } from 'vitest';
import { FilmHandoff } from './film-handoff';
it('waits for a final pose and a paint before forwarding the shared cinematic position', () => {
  const h = new FilmHandoff();
  expect(h.sample(90, 100, false, 0)).toBe(90);
  expect(h.sample(150, 100, false, 16)).toBe(100);
  expect(h.sample(150, 100, true, 32)).toBe(100);
  expect(h.sample(150, 100, true, 48)).toBe(150);
  expect(h.sample(90, 100, true, 64)).toBe(90);
  expect(h.sample(150, 100, true, 80)).toBe(100);
});
it('does not block history, direct navigation or a permanently unavailable decoder', () => {
  const h = new FilmHandoff();
  expect(h.sample(1000, 100, false, 0)).toBe(1000);
  h.sample(0, 100, false, 0);
  h.sample(150, 100, false, 16);
  expect(h.sample(150, 100, false, 616)).toBe(150);
  h.sample(0, 100, false, 700);
  h.skip();
  expect(h.sample(150, 100, false, 716)).toBe(150);
});
