import { afterEach, expect, it, vi } from 'vitest';
import { homepagePresentation } from './presentation';
import { sceneState } from './timeline/scene-state';
import type { HomepageMeasurements } from './timeline/measurements';
import type { SceneRegion } from './timeline/geometry';

// Write-only projection doubles: no layout, CSS engine, video or GPU simulation.
const face = () => ({
  dataset: {} as Record<string, string>,
  inert: false,
  hidden: false,
  style: {
    setProperty(name: string, value: string) {
      Object.assign(this, { [name]: value });
    },
    removeProperty(name: string) {
      Reflect.deleteProperty(this, name);
    },
    getPropertyValue(name: string) {
      return Reflect.get(this, name) ?? '';
    },
  } as unknown as CSSStyleDeclaration,
  contains: () => false,
  querySelectorAll: () => [],
});
afterEach(() => vi.unstubAllGlobals());
it.each([0, 1, 8])(
  'projects scene visibility, clipping, gallery eligibility and footer both directions with %i cards',
  (count) => {
    vi.stubGlobal('innerHeight', 900);
    vi.stubGlobal('innerWidth', 1440);
    vi.stubGlobal('document', { activeElement: null });
    const names = ['hero', 'statements', 'capabilities', 'mark', 'gallery', 'location'];
    const regions = names.map((name, i) => ({
      name,
      top: i * 2000,
      height: 2000,
      face: face(),
      node: {},
    })) as unknown as SceneRegion[];
    const wipe = { ...face(), children: [face(), face()] },
      space = face();
    const root = {
      querySelector: (selector: string) => (selector === '[data-scene-wipe]' ? wipe : space),
      querySelectorAll: () => regions.map((r) => r.face),
    } as unknown as HTMLElement;
    const geometry = Object.fromEntries(
      regions.map((r) => [r.name, { top: r.top, height: r.height }]),
    );
    const metrics = {
      end: 9800,
      boundary: 10000,
      cue: 8000,
      first: 8500,
      authoredFirst: 8500,
      step: 200,
      count,
      columns: 2,
    };
    const presentation = homepagePresentation(root, regions, {
      curtainCount: 2,
      surfaceHeight: 900,
      galleryClipLeft: 0,
      galleryClipTop: 0,
    } as HomepageMeasurements);
    const gallery = { count, empty: count === 0, update: vi.fn(), active: vi.fn() };
    const snapshot = (y: number) => {
      const frame = sceneState(y, geometry, metrics);
      presentation.render({
        y,
        frame,
        firstExit: 0,
        first: metrics.first,
        step: 200,
        gap: 200,
        wipeBands: [0.5, 0.8],
        gallery,
      });
      return regions.map((r) => ({
        visible: r.face.dataset.visible,
        inert: r.face.inert,
        transform: r.face.style.transform,
        exit: r.face.style.getPropertyValue?.('--gallery-exit'),
      }));
    };
    // Start at an explicit closed pose. Only compare observable writes at visible boundaries.
    const before = snapshot(0);
    expect(before[0]).toMatchObject({ visible: 'true', inert: false });
    expect(before[4]).toMatchObject({ visible: 'false', inert: true });
    snapshot(8500);
    expect(regions[4]!.face.dataset.visible).toBe('true');
    expect(regions[4]!.face.inert).toBe(false);
    expect(gallery.update).toHaveBeenCalled();
    expect(space.style.clipPath).toContain('polygon(');
    snapshot(9950);
    expect(regions[4]!.face.inert).toBe(true);
    expect(gallery.active).toHaveBeenLastCalledWith(false);
    snapshot(11800);
    expect(regions[5]!.face.style.transform).toBe('translateY(-700px)');
    snapshot(11000);
    expect(regions[5]!.face.style.transform).toBe('translateY(0px)');
    snapshot(8500);
    expect(regions[4]!.face.inert).toBe(false);
    expect(gallery.active).toHaveBeenLastCalledWith(true);
    snapshot(0);
    expect(regions[0]!.face.inert).toBe(false);
    expect(regions[4]!.face.inert).toBe(true);
    // These are the exact scene-face style contracts of the former canonical native sweep.
    for (const region of regions.slice(1, 5)) {
      snapshot(region.top + region.height * 0.35);
      const first = { ...region.face.style };
      expect(region.face.style.getPropertyValue('--local-progress')).toBe('0.35');
      snapshot(region.top + region.height * 0.7);
      expect(region.face.style.getPropertyValue('--local-progress')).toBe('0.7');
      snapshot(region.top + region.height * 0.35);
      expect({ ...region.face.style }).toEqual(first);
    }
    presentation.reduced();
    expect(regions.every((r) => !r.face.inert && r.face.dataset.visible === 'true')).toBe(true);
    expect(wipe.hidden).toBe(true);
    presentation.dispose();
    expect(regions.every((r) => !r.face.inert && r.face.dataset.visible === undefined)).toBe(true);
  },
);
