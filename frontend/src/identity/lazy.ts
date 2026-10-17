import type { physicalIdentity } from './physical';

/** Prepare near the scene; a late import may never outlive its page owner. */
export function lazyPhysicalIdentity(svg: SVGSVGElement, changed: () => void) {
  let instance: ReturnType<typeof physicalIdentity> | null = null;
  let pending: Promise<void> | null = null;
  let active = false,
    disposed = false,
    failed = false;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const prepare = () => {
    if (disposed || failed || reduced.matches || pending || instance) return;
    pending = import('./physical')
      .then(({ physicalIdentity }) => {
        if (disposed || reduced.matches) return;
        instance = physicalIdentity(svg);
        instance.active(active);
        changed();
      })
      .catch((error) => {
        failed = true;
        console.warn('Interactive identity unavailable; static identity retained', error);
      })
      .finally(() => {
        pending = null;
      });
  };
  return {
    prepare,
    active(value: boolean) {
      active = value;
      if (value) prepare();
      instance?.active(value);
    },
    dispose() {
      disposed = true;
      active = false;
      instance?.dispose();
      instance = null;
    },
  };
}
