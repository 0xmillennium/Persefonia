import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { navigationCover } from './cover';
import { clearNavigationMarker } from './marker';

vi.mock('../identity/compact/controller', () => ({
  compactIdentity: () => ({ dispose: vi.fn() }),
}));
vi.mock('../shared/motion/staircase', () => ({ exitStaircase: async () => {} }));
vi.mock('./marker', () => ({ rememberNavigation: () => true, clearNavigationMarker: vi.fn() }));
class ElementStub {
  listeners = new Map<string, (event: unknown) => void>();
  href = 'https://example.test/search';
  target = '';
  append() {}
  setAttribute() {}
  remove() {}
  hasAttribute() {
    return false;
  }
  closest() {
    return this;
  }
  addEventListener(type: string, listener: (event: unknown) => void) {
    this.listeners.set(type, listener);
  }
}
class Click {
  defaultPrevented = false;
  button = 0;
  metaKey = false;
  ctrlKey = false;
  altKey = false;
  shiftKey = false;
  target = new ElementStub();
  preventDefault() {
    this.defaultPrevented = true;
  }
}
let frames: FrameRequestCallback[], assign: ReturnType<typeof vi.fn>, events: ElementStub;
beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  frames = [];
  assign = vi.fn();
  events = new ElementStub();
  vi.stubGlobal('window', {
    setTimeout,
    clearTimeout,
    addEventListener: events.addEventListener.bind(events),
  });
  vi.stubGlobal('document', {
    createElement: () => new ElementStub(),
    importNode: () => new ElementStub(),
    body: new ElementStub(),
  });
  vi.stubGlobal(
    'DOMParser',
    class {
      parseFromString() {
        return { documentElement: new ElementStub() };
      }
    },
  );
  vi.stubGlobal('Element', ElementStub);
  vi.stubGlobal('MouseEvent', Click);
  vi.stubGlobal('location', { origin: 'https://example.test', pathname: '/', search: '', assign });
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => frames.push(callback));
  // Deliberately deliver cancelled callbacks too, as in an already dequeued browser frame.
  vi.stubGlobal('cancelAnimationFrame', vi.fn());
});
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
function setup() {
  const root = new ElementStub(),
    abort = new AbortController();
  const dispose = navigationCover(root as unknown as HTMLElement, abort.signal);
  const click = (url = 'https://example.test/search') => {
    const event = new Click();
    event.target.href = url;
    root.listeners.get('click')!(event);
    return event;
  };
  return { root, abort, dispose, click };
}
it('paints across two frames then navigates once', () => {
  const s = setup();
  expect(s.click().defaultPrevented).toBe(true);
  expect(assign).not.toHaveBeenCalled();
  frames[0]!(0);
  expect(assign).not.toHaveBeenCalled();
  frames[1]!(0);
  frames[1]!(0);
  expect(assign).toHaveBeenCalledExactlyOnceWith('https://example.test/search');
  s.abort.abort();
  s.dispose();
  expect(clearNavigationMarker).not.toHaveBeenCalled();
});
it.each(['dispose-first', 'dispose-second', 'abort', 'timeout', 'bfcache'])(
  '%s invalidates late queued navigation',
  (mode) => {
    const s = setup();
    s.click();
    if (mode === 'dispose-second') frames[0]!(0);
    if (mode.startsWith('dispose')) s.dispose();
    else if (mode === 'abort') s.abort.abort();
    else if (mode === 'timeout') vi.advanceTimersByTime(12000);
    else events.listeners.get('pageshow')!({ persisted: true });
    frames[0]!(0);
    frames[1]?.(0);
    expect(assign).not.toHaveBeenCalled();
    s.dispose();
  },
);
it('a replaced intent cannot navigate even if its queued frame is delivered', () => {
  const s = setup();
  s.click();
  frames[0]!(0);
  s.click('https://example.test/contact');
  frames[1]!(0);
  expect(assign).not.toHaveBeenCalled();
  frames[2]!(0);
  frames[3]!(0);
  expect(assign).toHaveBeenCalledExactlyOnceWith('https://example.test/contact');
  s.dispose();
});
