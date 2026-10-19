import { tunerView } from './tuner-view';
import { tunerInput } from './tuner-input';
import { collectionStop, nearestCollectionStop } from './tuner-stops';
import { gallerySettle } from './motion';
import { KINETICS, KineticMotion, releasePlan } from './kinetics';

/** A looping cylinder. Wheel ownership belongs to the beginning of each gesture. */
export function collectionTuner(
  directory: HTMLElement,
  select: (value: string, direction: number) => void,
) {
  const abort = new AbortController();
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const visual = tunerView(directory);
  const { button, radios } = visual;
  let committed = 0,
    view = 0,
    target = 0,
    pending: number | null = null;
  let frame = 0,
    previous = 0;
  let motion: KineticMotion | null = null;
  const wrapped = (index: number) => collectionStop(index, radios.length);
  function render(position: number) {
    view = position;
    visual.render(position);
  }
  function stop() {
    cancelAnimationFrame(frame);
    frame = previous = 0;
    motion = null;
    delete directory.dataset.tunerMotion;
  }
  function tick(now: number) {
    frame = 0;
    const seconds = previous ? Math.min(0.05, (now - previous) / 1000) : 1 / 60;
    previous = now;
    if (motion) {
      const phase = motion.phase(now),
        to = motion.plan.to;
      render(motion.sample(now));
      directory.dataset.tunerMotion = phase;
      if (phase === 'resting') {
        motion = null;
        delete directory.dataset.tunerMotion;
        previous = 0;
        choose(to);
      } else frame = requestAnimationFrame(tick);
      return;
    }
    render(gallerySettle(view, target, seconds));
    if (view !== target) frame = requestAnimationFrame(tick);
    else previous = 0;
  }
  function settle(index: number, animate = true) {
    stop();
    target = index;
    if (!animate || reduced.matches || document.hidden) {
      stop();
      render(index);
    } else if (!frame && view !== target) frame = requestAnimationFrame(tick);
  }
  const open = (value: boolean) => visual.open(value);
  function choose(index: number) {
    stop();
    const next = wrapped(index);
    open(false);
    if (next !== wrapped(committed)) {
      pending = index;
      select(radios[next]!.value, Math.sign(index - committed));
      pending = null;
    } else {
      committed = index;
      settle(index);
    }
  }
  const input = tunerInput(
    directory,
    visual,
    reduced,
    abort.signal,
    () => ({ position: view, committed, moving: !!motion }),
    {
      stop,
      choose,
      preview: render,
      restore(animate) {
        settle(committed, animate);
      },
      release(speed) {
        stop();
        motion = new KineticMotion(releasePlan(view, speed, KINETICS.tuner), performance.now());
        directory.dataset.tunerMotion = motion.phase(performance.now());
        frame = requestAnimationFrame(tick);
      },
    },
  );
  const observer = new ResizeObserver(() => {
    if (visual.measure()) input.cancel();
    render(view);
  });
  observer.observe(button);
  return {
    active(value: boolean) {
      if (!value) {
        input.cancel();
        open(false);
      }
    },
    update(value: string, animate = true) {
      const index = radios.findIndex((radio) => radio.value === value);
      if (index < 0) return;
      committed =
        pending !== null && wrapped(pending) === index
          ? pending
          : nearestCollectionStop(committed, index, radios.length);
      visual.label(index);
      settle(committed, animate);
    },
    select(value: string) {
      const index = radios.findIndex((radio) => radio.value === value);
      if (index >= 0) choose(nearestCollectionStop(committed, index, radios.length));
    },
    wheel: input.wheel,
    dispose() {
      input.finish(true);
      stop();
      abort.abort();
      observer.disconnect();
      visual.dispose();
    },
  };
}
