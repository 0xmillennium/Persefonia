import { describe, expect, it } from 'vitest';
import { wheelPath, wheelDestination } from './wheel-path';
import { WheelGlide } from './wheel-glide';

describe('homepage wheel path', () => {
  it('spends one curtain-only notch before the three left-side entrance slots and first center', () => {
    const first = 6954,
      step = 290;
    const stops = wheelPath(
      5341,
      [5838, ...Array.from({ length: 9 }, (_, i) => first + i * step)],
      10307,
      11207,
      120,
    );
    const entrance = stops.slice(stops.indexOf(5341), stops.indexOf(5341) + 6);
    expect(entrance).toEqual([5341, 5838, first, first + step, first + step * 2, first + step * 3]);
    let y = entrance[5]!;
    for (let notch = 4; notch >= 0; notch--) {
      y = wheelDestination(stops, y, -1);
      expect(y).toBe(entrance[notch]);
    }
  });
  for (const count of [0, 1, 2, 3, 8, 20]) {
    it(`retraces every stop through film, ${count} gallery cards and footer`, () => {
      const gallery = Array.from({ length: count + 6 }, (_, i) => 6900 + i * 181.37);
      const footer = gallery[gallery.length - 1]! + 900.4;
      const stops = wheelPath(5341.3, gallery, footer, footer + 905.2, 120);
      const forward = [0];
      while (forward[forward.length - 1] !== stops[stops.length - 1])
        forward.push(wheelDestination(stops, forward[forward.length - 1]!, 1));
      const backward = [forward[forward.length - 1]!];
      while (backward[backward.length - 1] !== 0)
        backward.push(wheelDestination(stops, backward[backward.length - 1]!, -1));
      expect(backward).toEqual([...forward].reverse());
      expect(forward).toEqual(stops);
      expect(stops.every((y, i) => Number.isInteger(y) && (!i || y > stops[i - 1]!))).toBe(true);
      expect(
        stops.slice(stops.indexOf(Math.round(gallery[0]!)), stops.indexOf(Math.round(footer))),
      ).toEqual(gallery.map(Math.round));
    });
  }

  it('counts input from the pending destination even when reversing an unfinished glide', () => {
    const stops = wheelPath(5341, [6900, 7100, 7300], 8200, 9100, 120);
    const glide = new WheelGlide();
    let y = 5280;
    for (const [i, direction] of [1, 1, 1, -1, -1, -1].entries()) {
      const origin = glide.active ? glide.target : y;
      const next = wheelDestination(stops, origin, direction);
      glide.cancel();
      glide.retarget(y, next - y, 0, 9100, i * 10);
      y = glide.sample(i * 10 + 5);
    }
    expect(glide.target).toBe(5280);
    expect(glide.sample(1000)).toBe(5280);
  });

  it('rejoins the ordered path after native input and clamps at either document boundary', () => {
    const stops = wheelPath(5341, [6900, 7100], 8200, 9100, 120);
    expect(wheelDestination(stops, 6017, 1)).toBe(6900);
    expect(wheelDestination(stops, 6017, -1)).toBe(5341);
    expect(wheelDestination(stops, 0, -1)).toBe(0);
    expect(wheelDestination(stops, 9100, 1)).toBe(9100);
  });
});
