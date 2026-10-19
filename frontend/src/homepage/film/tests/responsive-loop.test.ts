import { beforeEach, expect, it, vi } from 'vitest';
import { ResponsiveLoop } from '../responsive-loop';
const fixture = vi.hoisted(() => {
  const created: FakeLoop[] = [];
  class FakeLoop {
    revision = 0;
    prepared = false;
    videoReady = false;
    reduced = false;
    staticReady = false;
    get readiness() {
      return { state: this.reduced && this.staticReady ? 'static' : 'loading', progress: 0 };
    }
    time = 0;
    failed = false;
    blocked = false;
    image = undefined;
    posterUrl = '';
    constructor(
      _parent: HTMLElement,
      readonly tier: number,
      readonly changed: () => void,
    ) {
      created.push(this);
    }
    prepare(_time = 0, reduced = false) {
      this.reduced = reduced;
      this.prepared = true;
    }
    setPlayback(_allowed: boolean, observe: boolean) {
      if (observe) this.revision++;
    }
    suspend() {}
    show() {}
    retry() {}
    dispose() {}
  }
  return { created, FakeLoop };
});
vi.mock('../loop-film', () => ({ LoopFilm: fixture.FakeLoop }));
beforeEach(() => {
  fixture.created.length = 0;
});
it('observes a watch-start revision immediately and never aliases a replacement with the old source', () => {
  const loop = new ResponsiveLoop({} as HTMLElement, 1280, vi.fn());
  loop.prepare();
  const before = loop.revision;
  loop.setPlayback(true, true);
  expect(loop.revision).toBeGreaterThan(before);
  const old = loop.revision;
  loop.setTier(1920);
  loop.prepare();
  expect(loop.revision).toBe(old);
  const replacement = fixture.created[1]!;
  replacement.videoReady = true;
  replacement.revision = fixture.created[0]!.revision;
  replacement.changed();
  expect(loop.revision).toBeGreaterThan(old);
  loop.dispose();
});

it('applies a live reduced-motion change to a pending replacement and promotes its static image', () => {
  const loop = new ResponsiveLoop({} as HTMLElement, 1280, vi.fn());
  loop.prepare();
  loop.setTier(1920);
  loop.prepare();
  const replacement = fixture.created[1]!;
  const dispose = vi.spyOn(fixture.created[0]!, 'dispose');
  loop.prepare(true);
  expect(replacement.reduced).toBe(true);
  replacement.staticReady = true;
  replacement.changed();
  expect(dispose).toHaveBeenCalledOnce();
  expect(loop.readiness.state).toBe('static');
  loop.dispose();
});
