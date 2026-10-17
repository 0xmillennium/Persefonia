import { Hold } from './hold/state';
import { HoldClock } from './hold/clock';
import { HoldMaterial } from './hold/material';
import { LiquidField } from './rendering/field';
import { Renderer } from './rendering/renderer';
import { filmGestures } from './gestures';
import { applyHeroOpening, filmCoverBox, heroOpening, type HeroWritingBounds } from './cutouts';
import { ScrollFilms } from './scroll-films';
import {
  BackgroundTransport,
  isBackground,
  isScrollFilm,
  nextBackground,
  type Background,
  type BackgroundKey,
} from './backgrounds';
import { ResponsiveLoop } from './responsive-loop';
import { filmTier, loopTier } from './delivery';
/** Approved transport and material; native scene coordinator is the only scroll owner. */
export function mountFilm(root: HTMLElement, ready: () => void, selectionChanged: () => void) {
  const canvas = root.querySelector<HTMLCanvasElement>('[data-film-canvas]')!;
  const environment = root.querySelector<HTMLElement>('.home-environment')!;
  const status = root.querySelector<HTMLElement>('[data-film-status]')!;
  const notice = root.querySelector<HTMLOutputElement>('[data-film-notice]')!;
  const instructions = root.querySelector<HTMLElement>('#film-instructions')!;
  const countdown = root.querySelector<HTMLElement>('[data-hold-countdown]')!;
  const sheetImage = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
  const sheet = root.querySelector<HTMLElement>('.home-cinema__image')!;
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
    height = viewport.height,
    renderer: Renderer<BackgroundKey, Background> | null = null;
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
  let needsDraw = true,
    surfacePainted = false;
  let sheetGeneration = 0,
    sheetSource = '';
  let expansion = 0;
  let sheetOpening = '';
  let prefetchedFrom: Background | null = null;
  const video = new ScrollFilms(root.querySelector<HTMLElement>('.home-environment')!, tier, () => {
    request();
    wake();
  });
  let loopDelivery = loopTier(width, height);
  const loop = new ResponsiveLoop(
    root.querySelector<HTMLElement>('.home-environment')!,
    loopDelivery,
    () => wake(),
  );
  const posterPose = () => (expansion < 1 ? ('opening' as const) : ('final' as const));
  const presentationKey = (film: Background): BackgroundKey =>
    film === 'waterfall'
      ? `waterfall:${loop.revision}`
      : (video.presentation(film, transport.desired, posterPose())?.key ??
        `${film}:${transport.desired}`);
  const imageFor = (film: Background) =>
    isScrollFilm(film)
      ? video.presentation(film, transport.desired, posterPose())?.source
      : loop.image;
  const syncLoop = (effects = false) => {
    const selected = transport.film === 'waterfall';
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
  async function loadSheet() {
    const target = renderer;
    if (!target || sheetSource === sheetImage.currentSrc) return;
    const generation = ++sheetGeneration;
    try {
      await sheetImage.decode();
      const source = sheetImage.currentSrc;
      const bitmap = await createImageBitmap(sheetImage);
      try {
        if (disposed || renderer !== target || generation !== sheetGeneration) return;
        target.setSheet(bitmap);
        sheetSource = source;
        needsDraw = true;
        wake();
      } finally {
        bitmap.close();
      }
    } catch {
      /* The DOM photo and its static cutout remain the fallback. */
    }
  }
  const request = () => {
    // A history return can start beyond the film region. Prepare and
    // submit its restored target once so readiness and reverse re-entry
    // work; later off-scene media preparation stays suspended.
    if (disposed || suspended || (!filmVisible && !first)) return;
    const scenes =
      hold.preparing || prefetchedFrom === transport.film
        ? [transport.film, nextBackground(transport.film)]
        : [transport.film];
    if (scenes.includes('waterfall')) loop.prepare();
    video.setReduced(reduced.matches);
    for (const film of scenes.filter(isScrollFilm))
      video.request(film, transport.desired, posterPose());
  };
  const initialize = () => {
    needsDraw = true;
    surfacePainted = false;
    renderer?.dispose();
    renderer = null;
    sheetSource = '';
    delete root.dataset.gpuSheet;
    try {
      if (!reduced.matches) {
        renderer = new Renderer<BackgroundKey, Background>(canvas, false, true);
        renderer.resize(width, height);
      }
      canvas.hidden = !renderer;
      failed = false;
    } catch {
      renderer?.dispose();
      renderer = null;
      canvas.hidden = true;
      failed = true;
    }
    request();
    void loadSheet();
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
    if (reduced.matches && renderer) {
      renderer.dispose();
      renderer = null;
      canvas.hidden = true;
      delete root.dataset.gpuSheet;
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
    const fluid = renderer?.fluid ?? field;
    const fluidChanged = fluid.active,
      holdChanged = hold.animating;
    const uploads = renderer?.uploads;
    syncLoop(!!renderer && !reduced.matches && (fluid.active || hold.animating));
    fluid.step(elapsed);
    material.step(hold);
    const activeKey = presentationKey(transport.film),
      nextKey = presentationKey(nextBackground(transport.film));
    const active = imageFor(transport.film);
    const next = hold.preparing ? imageFor(nextBackground(transport.film)) : undefined;
    const paired = !!active && !!next;
    try {
      if (
        renderer &&
        (fluid.active ||
          hold.animating ||
          (isScrollFilm(transport.film) && video.failed(transport.film)))
      ) {
        if (active && (hold.phase !== 'REVEALING' || paired)) renderer.upload(0, activeKey, active);
        if (paired) renderer.upload(1, nextKey, next!);
      }
      if (hold.step(clock.frame(now), paired, material.coverage, reduced.matches || !renderer)) {
        transport.switchFilm();
        renderer?.swap();
        syncLoop(!!renderer && !reduced.matches && (fluid.active || hold.animating));
        selectionChanged();
        request();
      }
      material.step(hold);
      const key = presentationKey(transport.film);
      const bounds = filmCoverBox(width, height, transport.film);
      const nativeAvailable = isScrollFilm(transport.film)
        ? video.hasVisual(transport.film, transport.desired, posterPose())
        : !!loop.image;
      const native =
        nativeAvailable && (reduced.matches || !renderer || (!fluid.active && !hold.animating));
      if (native) {
        video.show(
          isScrollFilm(transport.film) ? transport.film : null,
          transport.desired,
          posterPose(),
          bounds,
          filmVisible ? opacity : 0,
        );
        loop.show(transport.film === 'waterfall', bounds, filmVisible ? opacity : 0);
        canvas.style.opacity = '0';
        surfacePainted = false;
        delete root.dataset.gpuSheet;
        needsDraw = true;
        if (imageFor(transport.film) && transport.present(key) && first) {
          first = false;
          ready();
        }
      } else {
        if (renderer?.residentKeys[0] === key) canvas.style.opacity = '';
        const draw =
          (surfacePainted || renderer?.residentKeys[0] === key) &&
          (needsDraw ||
            fluidChanged ||
            holdChanged ||
            fluid.active ||
            hold.animating ||
            renderer?.uploads !== uploads);
        const opening = heroOpening(width, height, expansion, transport.film, writingBounds);
        const incoming = heroOpening(
          width,
          height,
          expansion,
          nextBackground(transport.film),
          writingBounds,
        );
        const submitted =
          draw &&
          renderer?.draw({
            field: fluid,
            material,
            hold,
            time: now / 1000,
            reveal: hold.phase === 'REVEALING',
            reduced: reduced.matches,
            current: transport.film,
            next: nextBackground(transport.film),
            aperture: renderer.hasSheet ? opening.box : undefined,
            circles: renderer.hasSheet ? opening.circles : undefined,
            nextAperture: renderer.hasSheet ? incoming.box : undefined,
            nextCircles: renderer.hasSheet ? incoming.circles : undefined,
            extraAperture: renderer.hasSheet ? opening.extraBox : undefined,
            nextExtraAperture: renderer.hasSheet ? incoming.extraBox : undefined,
            dividers: renderer.hasSheet ? opening.dividers : undefined,
            nextDividers: renderer.hasSheet ? incoming.dividers : undefined,
            capsules: renderer.hasSheet ? opening.capsules : undefined,
            nextCapsules: renderer.hasSheet ? incoming.capsules : undefined,
            filmBounds: bounds,
            nextFilmBounds: filmCoverBox(width, height, nextBackground(transport.film)),
            imageOpacity: filmVisible ? opacity : 0,
          });
        if (submitted) {
          needsDraw = false;
          surfacePainted = true;
        }
        if (submitted && renderer?.residentKeys[0] === key) {
          video.show(null, transport.desired, posterPose(), bounds, 0);
          loop.show(false, bounds, 0);
          canvas.hidden = false;
          transport.present(key);
          // The gallery curtain covers this entire environment. Keep
          // fluid state alive underneath so a quick reversal reveals it.
          if (renderer.hasSheet) root.dataset.gpuSheet = 'true';
          if (first) {
            first = false;
            ready();
          }
        }
      }
    } catch {
      renderer?.dispose();
      renderer = null;
      canvas.hidden = true;
      delete root.dataset.gpuSheet;
      hold.reset();
      failed = true;
      request();
      wake();
    }
    const openingKey = `${transport.film}:${width}:${height}:${expansion}:${JSON.stringify(writingBounds)}`;
    if (!first && sheetOpening !== openingKey) {
      applyHeroOpening(
        sheet,
        width,
        height,
        heroOpening(width, height, expansion, transport.film, writingBounds),
      );
      sheetOpening = openingKey;
    }
    const displayed = transport.presented?.split(':');
    const frameLabel =
      transport.film === 'waterfall'
        ? 'Waterfall · loop'
        : displayed
          ? `${displayed[0] === 'tree' ? 'Tree' : 'Robotic arm'} · ${String(Number(displayed[1]) + 1).padStart(3, '0')} / 192`
          : 'Preparing first film frame';
    const scrollPresentation = isScrollFilm(transport.film)
      ? video.presentation(transport.film, transport.desired, posterPose())
      : undefined;
    const staticMessage =
      scrollPresentation?.kind === 'poster'
        ? `${transport.film === 'tree' ? 'Tree' : 'Robotic arm'} · static ${scrollPresentation.key.endsWith(':opening') ? 'opening' : 'final'} poster.${reduced.matches ? '' : ' Select the film again to retry.'}`
        : '';
    const failureMessage =
      hold.failure ??
      (transport.film === 'waterfall' && (loop.failed || loop.blocked)
        ? 'Waterfall · playback unavailable; still frame retained. Hold the background or select the film again to retry.'
        : isScrollFilm(transport.film) &&
            video.unavailable(transport.film, posterPose()) &&
            !scrollPresentation
          ? 'Film unavailable. Content and navigation remain available; select the film again to retry.'
          : '');
    const message = failureMessage || staticMessage || frameLabel;
    root.dataset.filmPresentation =
      scrollPresentation?.kind ??
      (transport.film === 'waterfall' && loop.image
        ? loop.videoReady
          ? 'video'
          : 'poster'
        : isScrollFilm(transport.film) && video.unavailable(transport.film, posterPose())
          ? 'unavailable'
          : 'loading');
    if (status.textContent !== message) status.textContent = message;
    const announcement = failureMessage || (!reduced.matches ? staticMessage : '');
    if (notice.textContent !== announcement) notice.textContent = announcement;
    countdown.hidden = hold.countdown === null;
    if (hold.countdown !== null) {
      countdown.textContent = String(hold.countdown);
      countdown.style.transform = `translate(${hold.center.x}px,${hold.center.y}px)`;
    }
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
        needsDraw = true;
      },
      retry(modality) {
        if (video.hasError) {
          video.retry();
          hold.retry();
        }
        if (
          (modality === 'pointer' ? transport.film : nextBackground(transport.film)) === 'waterfall'
        )
          loop.retry();
      },
      prefetch(from) {
        prefetchedFrom = from;
        request();
      },
      motion(sample) {
        (renderer?.fluid ?? field).inject(sample);
      },
    },
  );
  const release = () => gestures.release();
  reduced.addEventListener(
    'change',
    () => {
      release();
      hold.reset();
      (renderer?.fluid ?? field).clear();
      if (reduced.matches) {
        renderer?.dispose();
        renderer = null;
        canvas.hidden = true;
        delete root.dataset.gpuSheet;
      } else if (!renderer) initialize();
      request();
      needsDraw = true;
      wake();
    },
    options,
  );
  canvas.addEventListener(
    'webglcontextlost',
    (e) => {
      e.preventDefault();
      release();
      renderer?.dispose();
      renderer = null;
      canvas.hidden = true;
      delete root.dataset.gpuSheet;
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
      void loadSheet();
    },
    options,
  );
  initialize();
  instructions.hidden = false;
  root.setAttribute('aria-keyshortcuts', 'F');
  root.setAttribute('aria-describedby', 'film-instructions');
  return {
    update(
      progress: number,
      alpha: number,
      visible: boolean,
      allowHold: boolean,
      openingProgress: number,
      now = performance.now(),
      fullyCovered = false,
    ) {
      if (opacity !== alpha || filmVisible !== visible || expansion !== openingProgress)
        needsDraw = true;
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
    resize() {
      const bounds = environment.getBoundingClientRect();
      const nextWriting = measureWriting();
      if (JSON.stringify(writingBounds) !== JSON.stringify(nextWriting)) {
        writingBounds = nextWriting;
        needsDraw = true;
        wake();
      }
      if (width === bounds.width && height === bounds.height) return;
      width = bounds.width;
      height = bounds.height;
      tier = filmTier(width, height, tier);
      video.setTier(tier);
      loopDelivery = loopTier(width, height, loopDelivery);
      loop.setTier(loopDelivery);
      request();
      release();
      hold.reset();
      clock.reset();
      material = new HoldMaterial(width, height);
      field = new LiquidField(width, height);
      renderer?.resize(width, height);
      needsDraw = true;
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
      needsDraw = true;
      (renderer?.fluid ?? field).step((performance.now() - pausedAt) / 1000);
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
      renderer?.dispose();
      canvas.hidden = true;
      field.clear();
      sheetGeneration++;
      delete root.dataset.gpuSheet;
      delete root.dataset.filmPresentation;
      notice.textContent = '';
      instructions.hidden = true;
      root.removeAttribute('aria-keyshortcuts');
      root.removeAttribute('aria-describedby');
      sheet.style.removeProperty('clip-path');
      sheet.style.removeProperty('mask-image');
      sheet.style.removeProperty('mask-composite');
    },
  };
}
