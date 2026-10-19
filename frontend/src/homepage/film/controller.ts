import { Hold } from './hold/state';
import { HoldClock } from './hold/clock';
import { HoldMaterial } from './hold/material';
import { LiquidField } from './rendering/field';
import { FilmPresentation } from './presentation';
import { filmGestures } from './gestures';
import type { HeroWritingBounds } from './cutouts';
import { ScrollFilms } from './scroll-films';
import {
  BackgroundTransport,
  isBackground,
  isLoopBackground,
  isScrollFilm,
  nextBackground,
  type Background,
} from './backgrounds';
import { ResponsiveLoop } from './responsive-loop';
import { filmTier, loopTier } from './delivery';
export interface FilmOptions {
  root: HTMLElement;
  ready: () => void;
  selectionChanged: () => void;
  mediaChanged?: () => void;
}

export interface FilmFrameState {
  progress: number;
  alpha: number;
  visible: boolean;
  allowHold: boolean;
  openingProgress: number;
  now?: number;
  fullyCovered?: boolean;
}

/** Approved transport and material; native scene coordinator is the only scroll owner. */
export function mountFilm({ root, ready, selectionChanged, mediaChanged }: FilmOptions) {
  const canvas = root.querySelector<HTMLCanvasElement>('[data-film-canvas]')!;
  const environment = root.querySelector<HTMLElement>('.home-environment')!;
  const instructions = root.querySelector<HTMLElement>('#film-instructions')!;
  const sheetImage = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
  const abort = new AbortController(),
    options = { signal: abort.signal };
  const transport = new BackgroundTransport(),
    hold = new Hold(),
    clock = new HoldClock();
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const saved = history.state?.homepageV2?.film;
  if (isBackground(saved)) transport.restore(saved);
  // CSS excludes a reserved scrollbar gutter and uses the stable viewport
  // height. Native media, cutouts and the shader must share that exact box.
  const viewport = environment.getBoundingClientRect();
  let tier = filmTier(viewport.width, viewport.height);
  const writing = root.querySelector<HTMLElement>('.hero-statement')!;
  const measureWriting = (): HeroWritingBounds => {
    const environmentBounds = environment.getBoundingClientRect();
    const origin = environmentBounds.top;
    const bounds = writing.getBoundingClientRect();
    return {
      top: bounds.top - origin,
      bottom: bounds.bottom - origin,
      right:
        Math.max(
          ...[...writing.querySelectorAll('.hero-writing-text')].map(
            (node) => node.getBoundingClientRect().right,
          ),
        ) - environmentBounds.left,
      ceiling: document.querySelector('.home-nav')!.getBoundingClientRect().bottom - origin + 16,
      floor: root.querySelector('.hero-scroll')!.getBoundingClientRect().top - origin - 16,
    };
  };
  let writingBounds = measureWriting();
  let width = viewport.width,
    height = viewport.height;
  let material = new HoldMaterial(width, height),
    field = new LiquidField(width, height);
  let raf = 0,
    previous = 0,
    disposed = false,
    suspended = document.hidden;
  let pausedAt = performance.now();
  let opacity = 0,
    filmVisible = true,
    holdEnabled = true,
    first = true,
    failed = false;
  let covered = false;
  let expansion = 0;
  let prefetchedFrom: Background | null = null;
  const video = new ScrollFilms(root.querySelector<HTMLElement>('.home-environment')!, tier, () => {
    request();
    wake();
    mediaChanged?.();
  });
  let loopDelivery = loopTier(width, height);
  const loop = new ResponsiveLoop(
    root.querySelector<HTMLElement>('.home-environment')!,
    loopDelivery,
    () => wake(),
  );
  const posterPose = () => (expansion < 1 ? ('opening' as const) : ('final' as const));
  const presentation = new FilmPresentation(root, canvas, video, loop, () => wake());
  const syncLoop = (effects = false) => {
    const selected = isLoopBackground(transport.film);
    const menu = document.documentElement.dataset.menuOpen === 'true';
    loop.setPlayback(
      selected && filmVisible && !covered && !suspended && !menu && !reduced.matches,
      effects && selected,
    );
  };
  const menuObserver = new MutationObserver(() => {
    syncLoop();
    wake();
  });
  menuObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-menu-open'],
  });
  const request = () => {
    // A history return can start beyond the film region. Prepare and
    // submit its restored target once so readiness and reverse re-entry
    // work; later off-scene media preparation stays suspended.
    if (disposed || suspended || (!filmVisible && !first)) return;
    const scenes =
      hold.preparing || prefetchedFrom === transport.film
        ? [transport.film, nextBackground(transport.film)]
        : [transport.film];
    if (scenes.some(isLoopBackground)) loop.prepare();
    video.setReduced(reduced.matches);
    for (const film of scenes.filter(isScrollFilm))
      video.request(film, transport.desired, posterPose());
  };
  const initialize = () => {
    try {
      presentation.initialize(width, height, reduced.matches);
      failed = false;
    } catch {
      presentation.disable();
      failed = true;
    }
    request();
    void presentation.loadSheet();
    wake();
  };
  const wake = () => {
    if (!raf && !disposed && !suspended) raf = requestAnimationFrame(tick);
  };
  const tick = (now: number) => {
    raf = 0;
    if (disposed || suspended) return;
    // Browser preference state can precede its queued change notification.
    // No scheduled scene may keep a GPU surface alive after reduced motion wins.
    if (reduced.matches && presentation.available) {
      presentation.disable();
      hold.reset();
      field.clear();
      request();
    }
    const instruction = reduced.matches
      ? 'Press F while the main content is focused to change the static film image. Repeat to retry if a film is unavailable.'
      : 'Hold the background to change film, or press F while the main content is focused. Repeat to retry if a film is unavailable.';
    if (instructions.textContent !== instruction) instructions.textContent = instruction;
    const elapsed = previous ? (now - previous) / 1000 : 0;
    previous = now;
    const fluid = presentation.fluid ?? field;
    const fluidChanged = fluid.active,
      holdChanged = hold.animating;
    const uploads = presentation.uploads;
    syncLoop(presentation.available && !reduced.matches && (fluid.active || hold.animating));
    fluid.step(elapsed);
    material.step(hold);
    try {
      const paired = presentation.prepare(transport, hold, fluid.active, posterPose());
      if (
        hold.step(
          clock.frame(now),
          paired,
          material.coverage,
          reduced.matches || !presentation.available,
        )
      ) {
        transport.switchFilm();
        presentation.swap();
        syncLoop(presentation.available && !reduced.matches && (fluid.active || hold.animating));
        selectionChanged();
        request();
      }
      material.step(hold);
      if (
        presentation.render({
          transport,
          hold,
          material,
          fluid,
          width,
          height,
          expansion,
          writingBounds,
          reduced: reduced.matches,
          filmVisible,
          opacity,
          now,
          fluidChanged,
          holdChanged,
          uploads,
        }) &&
        first
      ) {
        first = false;
        ready();
      }
    } catch {
      presentation.disable();
      hold.reset();
      failed = true;
      request();
      wake();
    }
    if (!first) presentation.photo(transport, width, height, expansion, writingBounds);
    presentation.describe(transport, hold, reduced.matches, expansion);
    if (fluid.active || hold.animating) wake();
    else previous = 0;
  };
  const gestures = filmGestures(
    root,
    hold,
    clock,
    () => ({
      enabled: holdEnabled,
      suspended,
      disposed,
      first,
      reduced: reduced.matches,
      visible: filmVisible,
      covered,
      width,
      height,
      film: transport.film,
      prefetchedFrom,
    }),
    {
      request,
      changed: wake,
      invalidate() {
        presentation.invalidate();
      },
      retry(modality) {
        if (video.hasError) {
          video.retry();
          hold.retry();
        }
        if (
          isLoopBackground(modality === 'pointer' ? transport.film : nextBackground(transport.film))
        )
          loop.retry();
      },
      prefetch(from) {
        prefetchedFrom = from;
        request();
      },
      motion(sample) {
        (presentation.fluid ?? field).inject(sample);
      },
    },
  );
  const release = () => gestures.release();
  reduced.addEventListener(
    'change',
    () => {
      release();
      hold.reset();
      (presentation.fluid ?? field).clear();
      if (reduced.matches) {
        presentation.disable();
      } else if (!presentation.available) initialize();
      request();
      presentation.invalidate();
      wake();
    },
    options,
  );
  canvas.addEventListener(
    'webglcontextlost',
    (e) => {
      e.preventDefault();
      release();
      presentation.disable();
      hold.reset();
      request();
      wake();
    },
    options,
  );
  canvas.addEventListener('webglcontextrestored', initialize, options);
  sheetImage.addEventListener(
    'load',
    () => {
      void presentation.loadSheet();
    },
    options,
  );
  initialize();
  instructions.hidden = false;
  root.setAttribute('aria-keyshortcuts', 'F');
  root.setAttribute('aria-describedby', 'film-instructions');
  return {
    get frameInterval() {
      return isScrollFilm(transport.film) ? video.frameInterval(transport.film) : 1000 / 30;
    },
    get finalReady() {
      if (!isScrollFilm(transport.film)) return true;
      const last = video.presentation(transport.film, 191, 'final');
      return last?.key === `${transport.film}:191` || last?.key === `${transport.film}:final`;
    },
    update({
      progress,
      alpha,
      visible,
      allowHold,
      openingProgress,
      now = performance.now(),
      fullyCovered = false,
    }: FilmFrameState) {
      if (opacity !== alpha || filmVisible !== visible || expansion !== openingProgress)
        presentation.invalidate();
      transport.setProgress(progress);
      opacity = alpha;
      filmVisible = visible;
      holdEnabled = allowHold;
      covered = fullyCovered;
      expansion = openingProgress;
      if (!allowHold && gestures.active) release();
      request();
      cancelAnimationFrame(raf);
      tick(now);
    },
    resize(preserveGesture = false) {
      const bounds = environment.getBoundingClientRect();
      const nextWriting = measureWriting();
      if (JSON.stringify(writingBounds) !== JSON.stringify(nextWriting)) {
        writingBounds = nextWriting;
        presentation.invalidate();
        wake();
      }
      if (width === bounds.width && height === bounds.height) return;
      width = bounds.width;
      height = bounds.height;
      if (!preserveGesture) {
        tier = filmTier(width, height, tier);
        video.setTier(tier);
        loopDelivery = loopTier(width, height, loopDelivery);
        loop.setTier(loopDelivery);
        release();
        hold.reset();
        clock.reset();
      }
      request();
      material = new HoldMaterial(width, height);
      field = new LiquidField(width, height);
      presentation.resize(width, height);
      presentation.invalidate();
      wake();
    },
    suspend() {
      release();
      pausedAt = performance.now();
      suspended = true;
      gestures.reset();
      syncLoop();
      cancelAnimationFrame(raf);
      raf = 0;
      previous = 0;
      video.suspend(true);
      loop.suspend(true);
      hold.reset();
      clock.reset();
    },
    resume() {
      if (disposed) return;
      suspended = false;
      video.suspend(false);
      loop.suspend(false);
      presentation.invalidate();
      (presentation.fluid ?? field).step((performance.now() - pausedAt) / 1000);
      request();
      wake();
    },
    canHold(target: Element) {
      return gestures.canHold(target);
    },
    get film() {
      return transport.film;
    },
    get failed() {
      return failed;
    },
    get hasFrame() {
      return !first;
    },
    dispose() {
      gestures.dispose();
      disposed = true;
      abort.abort();
      menuObserver.disconnect();
      cancelAnimationFrame(raf);
      video.dispose();
      loop.dispose();
      presentation.dispose();
      field.clear();
      instructions.hidden = true;
      root.removeAttribute('aria-keyshortcuts');
      root.removeAttribute('aria-describedby');
    },
  };
}
