import { afterEach, expect, it, vi } from 'vitest';
import { filmGestures } from '../gestures';
import { Hold } from '../hold/state';
import { HoldClock } from '../hold/clock';

afterEach(() => vi.unstubAllGlobals());
it('uses composition coordinates for both hover pigment and hold input after aperture translation', () => {
  const windowTarget = Object.assign(new EventTarget(), {
    setTimeout,
    clearTimeout,
    closest: () => null,
  });
  const root = Object.assign(new EventTarget(), {
    closest: () => null,
    setPointerCapture() {},
    hasPointerCapture: () => false,
    releasePointerCapture() {},
  });
  vi.stubGlobal('window', windowTarget);
  vi.stubGlobal('document', { documentElement: { dataset: {} } });
  const hold = new Hold();
  const samples: { x: number; y: number }[] = [];
  const gestures = filmGestures(
    root as unknown as HTMLElement,
    hold,
    new HoldClock(),
    () => ({
      enabled: true,
      suspended: false,
      disposed: false,
      first: false,
      reduced: false,
      visible: true,
      covered: false,
      width: 390,
      height: 1000,
      origin: { x: 0, y: -75 },
      film: 'tree',
      prefetchedFrom: 'tree',
    }),
    {
      invalidate() {},
      request() {},
      changed() {},
      retry() {},
      prefetch() {},
      motion: (sample) => samples.push(sample),
    },
  );
  for (const [x, y, timestamp] of [
    [100, 200, 100],
    [120, 220, 116],
  ]) {
    const event = Object.assign(new Event('pointermove'), {
      pointerId: 1,
      pointerType: 'mouse',
      clientX: x,
      clientY: y,
    });
    Object.defineProperty(event, 'timeStamp', { value: timestamp });
    windowTarget.dispatchEvent(event);
  }
  expect(samples).toHaveLength(1);
  expect(samples[0]).toMatchObject({ x: 120, y: 295 });
  root.dispatchEvent(
    Object.assign(new Event('pointerdown'), {
      pointerId: 1,
      pointerType: 'mouse',
      clientX: 120,
      clientY: 220,
      button: 0,
      isPrimary: true,
    }),
  );
  expect(hold.center).toEqual({ x: 120, y: 295 });
  gestures.dispose();
});
it.each([0, 220, 450])(
  'touch motion after %dms cancels a candidate without claiming native pan',
  (delay) => {
    let now = 0;
    const windowTarget = Object.assign(new EventTarget(), {
      setTimeout,
      clearTimeout,
      closest: () => null,
    });
    const root = Object.assign(new EventTarget(), {
      closest: () => null,
      setPointerCapture() {},
      hasPointerCapture: () => false,
      releasePointerCapture() {},
    });
    vi.stubGlobal('window', windowTarget);
    vi.stubGlobal('document', { documentElement: { dataset: {} } });
    vi.stubGlobal('performance', { now: () => now });
    const hold = new Hold();
    const gestures = filmGestures(
      root as unknown as HTMLElement,
      hold,
      new HoldClock(),
      () => ({
        enabled: true,
        suspended: false,
        disposed: false,
        first: false,
        reduced: false,
        visible: true,
        covered: false,
        width: 390,
        height: 850,
        film: 'tree',
        prefetchedFrom: null,
      }),
      { invalidate() {}, request() {}, changed() {}, retry() {}, prefetch() {}, motion() {} },
    );
    const fire = (target: EventTarget, type: string, properties: object) => {
      const event = Object.assign(new Event(type, { cancelable: true }), properties);
      Object.defineProperty(event, 'timeStamp', { value: now });
      target.dispatchEvent(event);
      return event;
    };
    fire(root, 'pointerdown', {
      button: 0,
      isPrimary: true,
      pointerId: 1,
      pointerType: 'touch',
      clientX: 100,
      clientY: 600,
    });
    fire(windowTarget, 'touchstart', { touches: [{ identifier: 1, clientX: 100, clientY: 600 }] });
    now = delay;
    const move = fire(root, 'touchmove', {
      touches: [{ identifier: 1, clientX: 103, clientY: 570 }],
    });
    expect(move.defaultPrevented).toBe(false);
    expect(hold.pressed).toBe(false);
    expect(hold.cycles).toBe(0);
    gestures.dispose();
  },
);
it.each([
  { duration: 100, movement: 0, end: 'pointerup', toggles: 1, recovering: false },
  { duration: 500, movement: 0, end: 'pointerup', toggles: 0, recovering: false },
  { duration: 100, movement: 20, end: 'pointerup', toggles: 0, recovering: false },
  { duration: 100, movement: 0, end: 'pointercancel', toggles: 0, recovering: false },
  { duration: 100, movement: 0, end: 'pointerup', toggles: 0, recovering: true },
])(
  'background tap arbitration: $duration ms, $movement px, $end, recovery=$recovering',
  ({ duration, movement, end, toggles, recovering }) => {
    let now = 0,
      actual = 0;
    const windowTarget = Object.assign(new EventTarget(), {
      setTimeout,
      clearTimeout,
      closest: () => null,
    });
    const root = Object.assign(new EventTarget(), {
      closest: () => null,
      setPointerCapture() {},
      hasPointerCapture: () => false,
      releasePointerCapture() {},
    });
    vi.stubGlobal('window', windowTarget);
    vi.stubGlobal('document', { documentElement: { dataset: {} } });
    vi.stubGlobal('performance', { now: () => now });
    const hold = new Hold();
    const gestures = filmGestures(
      root as unknown as HTMLElement,
      hold,
      new HoldClock(),
      () => ({
        enabled: true,
        suspended: false,
        disposed: false,
        first: false,
        reduced: false,
        visible: true,
        covered: false,
        width: 390,
        height: 850,
        film: 'waterfall',
        prefetchedFrom: null,
      }),
      {
        toggleMotion() {
          actual++;
        },
        invalidate() {},
        request() {},
        changed() {},
        retry() {
          return recovering;
        },
        prefetch() {},
        motion() {},
      },
    );
    const fire = (target: EventTarget, type: string, x: number) => {
      const event = Object.assign(new Event(type), {
        pointerId: 1,
        pointerType: 'touch',
        clientX: x,
        clientY: 600,
        button: 0,
        isPrimary: true,
      });
      Object.defineProperty(event, 'timeStamp', { value: now });
      target.dispatchEvent(event);
    };
    fire(root, 'pointerdown', 100);
    now = duration;
    fire(windowTarget, 'pointermove', 100 + movement);
    fire(windowTarget, end, 100 + movement);
    expect(actual).toBe(toggles);
    expect(hold.pressed).toBe(false);
    expect(hold.cycles).toBe(0);
    gestures.dispose();
  },
);
