import { homepagePresentation } from './presentation';
import { homepageReadiness } from './readiness';
import { GalleryHandoff } from './gallery/handoff';
import { homepageHistory } from './timeline/history';
import { HomepageMeasurements } from './timeline/measurements';
import { FilmHandoff } from './timeline/film-handoff';
import { FilmPacing } from './timeline/film-pacing';
import { sceneState } from './timeline/scene-state';
import { storyViewport } from './timeline/viewport';
import { DisposalScope } from '../shared/disposal';
import { mountFilm } from './film/controller';
import { mobileNavigation } from './navigation/mobile-menu';
import { lazyPhysicalIdentity } from '../identity/lazy';
import { compactIdentity } from '../identity/compact/controller';
import { mountGallery } from './gallery/controller';
import { mountFooter } from './footer/controller';
import { lazyGlobe } from './globe/lazy';
import { siteName, letterReels } from './navigation/lettering';
import { pointerMarker } from './navigation/pointer';
import {
  filmRange,
  writingPacing,
  writingBeat,
  footerPhase,
  HOMEPAGE_SCROLL_SPEED,
} from './timeline/pacing';
import { windowProgress } from './contracts/progress';
import { GALLERY_HANDOFF } from './contracts/choreography';
import { navigationCover } from '../navigation/cover';
import { scrollNavigation } from './timeline/scroll-navigation';
import { galleryWriting } from './gallery/writing';
import { laterWriting, galleryCurtain } from './writing/later';
/** Native scroll owns choreography. Every region is measured independently. */
export async function enhanceHomepage(): Promise<() => void> {
  const [first, second] = await Promise.all([
    import('./writing/first'),
    import('./writing/second'),
  ]);
  const scope = new DisposalScope();
  try {
    return mountHomepage(scope, { ...first, ...second });
  } catch (error) {
    scope.dispose();
    throw error;
  }
}
function mountHomepage(
  scope: DisposalScope,
  {
    firstWriting,
    secondWriting,
    WritingEntrance,
  }: typeof import('./writing/first') & typeof import('./writing/second'),
): () => void {
  const found = document.querySelector<HTMLElement>('[data-home-v2]');
  if (!found) return () => {};
  const root: HTMLElement = found;
  const lifetime = new AbortController(),
    options = { signal: lifetime.signal };
  const viewport = storyViewport(root);
  scope.add(() => viewport.dispose());
  const historyState = homepageHistory({
    viewport: () => ({ y: scrollY, width: viewport.width, height: viewport.height }),
  });
  const touch = matchMedia('(pointer: coarse)');
  scope.add(() => historyState.dispose());
  let pendingDestination = historyState.historyVisit ? '' : location.hash;
  let handledHash = location.hash;
  const pacing = writingPacing();
  scope.add(() => {
    lifetime.abort();
    delete root.dataset.enhanced;
    delete root.dataset.film;
    delete root.dataset.filmBackdrop;
  });
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  // Explicit gallery measurements already include their own layout writes.
  const observer = new ResizeObserver((entries) => {
    if (entries.some((entry) => entry.target === root && measurements.changed(root))) measure();
    else if (entries.some((entry) => measurements.changed(entry.target))) resizeSurfaces();
  });
  scope.add(() => observer.disconnect());
  const regions = [...root.querySelectorAll<HTMLElement>('[data-scene]')].map((node) => ({
    node,
    name: node.dataset.scene!,
    face: node.querySelector<HTMLElement>('.scene-face')!,
    top: 0,
    height: 0,
  }));
  const environment = root.querySelector<HTMLElement>('.home-environment')!;
  const image = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
  const loading = document.querySelector<HTMLElement>('[data-home-loader]')!;
  const physical = lazyPhysicalIdentity(
    root.querySelector<SVGSVGElement>('[data-physical-identity] svg')!,
    schedule,
  );
  scope.add(() => physical.dispose());
  const brandButton = document.querySelector<HTMLButtonElement>('[data-compact-identity] button')!;
  brandButton.disabled = false;
  scope.add(() => {
    brandButton.disabled = true;
  });
  const compact = compactIdentity(document.querySelector<HTMLElement>('[data-compact-identity]')!);
  scope.add(() => compact.dispose());
  const name = siteName(document.querySelector<HTMLElement>('[data-site-name]')!);
  const letterEffects = [name];
  scope.add(() => name.dispose());
  let film: ReturnType<typeof mountFilm>;
  const marker = pointerMarker((target) => film?.canHold(target) ?? false);
  scope.add(() => marker.dispose());
  for (const label of document.querySelectorAll<HTMLElement>('.home-nav [data-nav-label]')) {
    const letters = letterReels(label);
    letterEffects.push(letters);
    scope.add(() => letters.dispose());
    const link = label.closest<HTMLAnchorElement>('a')!;
    link.addEventListener(
      'pointerenter',
      (event) => {
        if (event.pointerType !== 'touch') link.dataset.previewActive = 'true';
      },
      options,
    );
    link.addEventListener(
      'pointerleave',
      () => {
        delete link.dataset.previewActive;
      },
      options,
    );
    scope.add(() => {
      delete link.dataset.previewActive;
    });
  }
  const entrances = Array.from(
    { length: 6 },
    (_, i) => new WritingEntrance(i === 3 ? 1600 : 1000, i === 4 || i === 5),
  );
  scope.add(() => entrances.forEach((entrance) => entrance.dispose()));
  const complete = (index: number, entry: number, exit: number, now: number) => {
    const entrance = entrances[index]!;
    const value = entrance.sample(entry, exit, now, reduced.matches);
    if (entrance.running) schedule();
    return value;
  };
  const writing = firstWriting(root);
  scope.add(() => writing.dispose());
  const perspective = secondWriting(root);
  scope.add(() => perspective.dispose());
  const later = laterWriting(root);
  scope.add(() => later.dispose());
  const footer = mountFooter(root);
  scope.add(() => footer.dispose());
  void document.fonts.ready.then(() => {
    if (lifetime.signal.aborted) return;
    for (const line of root.querySelectorAll<HTMLElement>(
      '.capabilities li>span, .location-links a>.footer-rise',
    )) {
      const letters = letterReels(line, true);
      letterEffects.push(letters);
      scope.add(() => letters.dispose());
    }
  });
  const wipe = root.querySelector<HTMLElement>('[data-scene-wipe]')!;
  const curtainBars = [...wipe.querySelectorAll<HTMLElement>('i')];
  let disposed = false,
    raf = 0,
    suspended = false;
  let positionRevision = 0;
  scope.add(() => {
    disposed = true;
    cancelAnimationFrame(raf);
    loading.hidden = true;
  });
  const locationRegion = regions.find((r) => r.name === 'location')!;
  const globe = lazyGlobe({
    stage: root.querySelector<HTMLElement>('.location-system')!,
    isActive: () =>
      reduced.matches
        ? scrollY + innerHeight >= locationRegion.top &&
          scrollY < locationRegion.top + locationRegion.height
        : footerPhase(scrollY, locationRegion.top, locationRegion.height, innerHeight) > 0.6 &&
          scrollY < locationRegion.top + locationRegion.height,
  });
  scope.add(() => globe.dispose());
  const filmRegion = regions[0]!;
  const markRegion = regions.find((r) => r.name === 'mark')!;
  const galleryRegion = regions.find((r) => r.name === 'gallery')!;
  const galleryText = galleryWriting(galleryRegion.node);
  scope.add(() => galleryText.dispose());
  const gallerySpace = galleryRegion.node.querySelector<HTMLElement>('.gallery-space')!;
  scope.add(() => {
    gallerySpace.style.removeProperty('clip-path');
    galleryRegion.node.style.removeProperty('--gallery-tail-distance');
  });
  // Collection tuning rebases card travel, never the authored curtain or the current page position.
  const projectsCount = root.querySelectorAll(
    '.gallery-category[data-category="projects"] .gallery-card',
  ).length;
  const handoff = new GalleryHandoff(
    projectsCount,
    history.state?.homepageV2,
    historyState.restoring,
  );
  const travel = handoff.travel;
  const galleryFirst = () => travel.first();
  const galleryStep = () => travel.step();
  const travelFirst = () => travel.travelFirst();
  const travelEnd = () => travel.end(gallery.count);
  const travelPosition = (p: number) => travel.travelPosition(p, gallery.count);
  const galleryCue = () =>
    Math.ceil(filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height).end) +
    measurements.curtainHeight * GALLERY_HANDOFF.cue;
  const galleryBoundary = (direction: number) =>
    direction < 0 ? markRegion.top + pacing.transition + pacing.reading : locationRegion.top;
  function moveGallery(top: number, transition: boolean) {
    scrolling.move(top, transition);
  }
  const gallery = mountGallery({
    root: galleryRegion.node,
    position(p) {
      if (p === 0 && scrollY < travelFirst() - galleryStep() - 0.5) {
        moveGallery(galleryBoundary(-1), true);
        return;
      }
      const top = travelPosition(p);
      moveGallery(top, Math.min(scrollY, top) < travelFirst() - galleryStep() - 0.5);
    },
    changed: schedule,
    leave(direction) {
      moveGallery(galleryBoundary(direction), direction < 0);
    },
    tune() {
      scrolling.cancel();
      // Tuner selections start with card one centered at the current page position.
      travel.centerAt(scrollY);
      measure();
      save(true);
    },
    restoreCategory: historyState.restoring,
  });
  scope.add(() => gallery.dispose());
  const scrolling = scrollNavigation({
    signal: options.signal,
    reduced,
    cancelRestore: () => historyState.cancel(),
    changed: schedule,
    tunerWheel: (event) => gallery.tunerWheel(event),
    beforeWheel: () => gallery.wheel(),
    path: () => ({
      readingEnd: galleryBoundary(-1),
      cue: galleryCue(),
      first: travelFirst(),
      step: galleryStep(),
      count: gallery.count,
      footer: galleryBoundary(1),
      bottom: document.documentElement.scrollHeight - innerHeight,
      filmStep:
        filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height).step *
        HOMEPAGE_SCROLL_SPEED,
    }),
  });
  function save(force = false) {
    historyState.save(
      {
        film: film?.film,
        category: gallery.category,
        pathCount: travel.pathCount,
        tuningShift: travel.tuningShift,
      },
      force,
    );
  }
  const filmHandoff = new FilmHandoff();
  const filmPacing = new FilmPacing();
  const measurements = new HomepageMeasurements({
    root,
    regions,
    environment,
    curtainBars,
    gallerySpace,
    viewport,
    reduced,
    touch,
    travel,
    count: () => gallery.count,
  });
  scope.add(() => measurements.dispose());
  const presentation = homepagePresentation(root, regions, measurements);
  scope.add(() => presentation.dispose());
  function measure(preserveGlide = false) {
    if (!preserveGlide) scrolling.cancel();
    measurements.measure();
    film?.resize();
    writing.measure();
    perspective.measure();
    measurements.resizeSurface();
    schedule();
    restorePosition();
  }
  function restorePosition() {
    if (readiness.complete && pendingDestination && !historyState.restoring) {
      const hash = pendingDestination;
      pendingDestination = '';
      return navigate(hash);
    }
    historyState.restore(readiness.complete, (y) => {
      filmPacing.reset();
      filmHandoff.skip();
      measure();
      scrollTo({ top: y, behavior: 'instant' });
      schedule();
    });
    return false;
  }
  const readiness = homepageReadiness(root, loading, historyState.historyVisit, reduced, measure);
  scope.add(() => readiness.dispose());
  root.dataset.enhanced = 'true';
  const mobileMenu = mobileNavigation();
  scope.add(mobileMenu);
  film = mountFilm({
    root,
    ready: (state) => readiness.media(state),
    selectionChanged: schedule,
    mediaChanged: schedule,
  });
  scope.add(() => film.dispose());
  readiness.track(image);
  const navigation = navigationCover(document.body, options.signal);
  scope.add(navigation);
  function schedule() {
    if (!raf && !disposed && !suspended) raf = requestAnimationFrame(update);
  }
  function update(now: number) {
    raf = 0;
    if (disposed || suspended) return;
    const revision = positionRevision;
    if (restorePosition()) return;
    // Keep the derived presentation flag consistent with the current query.
    // Scene wakeups and BFCache restoration reconcile it as well as change
    // notifications; stale derived state must not control measured measurements.geometry.
    const reducedState = String(reduced.matches);
    if (root.dataset.reduced !== reducedState) {
      root.dataset.reduced = reducedState;
      measure();
    }
    scrolling.update(now);
    if (
      !reduced.matches &&
      !historyState.restoring &&
      handoff.update(scrollY, galleryCue(), galleryBoundary(1), gallery)
    )
      measure(true);
    root.dataset.film = film.film;
    marker.refresh();
    const range = filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height);
    if (reduced.matches) filmPacing.reset();
    const cinematicY = reduced.matches
      ? scrollY
      : filmPacing.sample(scrollY, range.start, range.end, film.frameInterval, now);
    const y = reduced.matches
      ? scrollY
      : filmHandoff.sample(cinematicY, range.end, film.finalReady, now);
    if (filmPacing.moving || y !== scrollY) schedule();
    const firstBeat = writingBeat(y, filmRegion.top, true);
    writing.update(firstBeat.exit, reduced.matches);
    if (reduced.matches) {
      entrances.forEach((entrance) => entrance.sample(1, 0, now, true));
      galleryText.update(1, 0, true);
      perspective.update(1, 0, true);
      later.third(1, 0, true);
      later.fourth(1, 0, 1, true);
      footer.update(1, true);
      delete root.dataset.filmBackdrop;
      film.update({
        progress: 0,
        alpha: 1,
        visible: true,
        allowHold: false,
        openingProgress: 0,
        now,
      });
      if (revision !== positionRevision) return;
      presentation.reduced();
      gallery.active(false);
      physical.active(false);
      globe.approach(y + innerHeight >= locationRegion.top - innerHeight);
      globe.active(
        y + innerHeight >= locationRegion.top && y < locationRegion.top + locationRegion.height,
      );
      save();
      return;
    }
    const frame = sceneState(y, measurements.geometry, {
      end: travelEnd(),
      boundary: galleryBoundary(1),
      cue: galleryCue(),
      first: travelFirst(),
      authoredFirst: galleryFirst(),
      step: galleryStep(),
      count: gallery.count,
      columns: measurements.curtainCount,
    });
    const { expansion, entrance } = frame;
    const galleryP = frame.galleryProgress;
    const coverPhase = entrance.phase;
    const wipeBands = galleryCurtain(coverPhase, measurements.curtainCount);
    const curtainSolid = entrance.darkness;
    const locationPhase = footerPhase(y, locationRegion.top, locationRegion.height, innerHeight);
    const locationLead = innerHeight * 0.1;
    const galleryExit = windowProgress(galleryP, 0.83, 0.9);
    galleryText.update(complete(4, entrance.writing, galleryExit, now), galleryExit, false);
    const globeActive = footer.update(
      locationPhase,
      false,
      complete(5, Math.max(0, locationPhase), 0, now),
    );
    const pastEnvironment = y >= galleryRegion.top;
    root.dataset.filmBackdrop = expansion === 1 && curtainSolid < 0.5 ? film.film : '';
    film.update({
      progress: frame.film,
      alpha: 1,
      visible: true,
      allowHold: !pastEnvironment,
      openingProgress: expansion,
      now,
      fullyCovered: wipeBands.every((p) => p === 1),
    });
    // Media readiness can synchronously navigate while film.update is running.
    if (revision !== positionRevision) return;
    const { second: secondBeat, third: thirdBeat, fourth: fourthBeat } = frame;
    if (y + innerHeight >= markRegion.top - innerHeight) physical.prepare();
    perspective.update(complete(0, secondBeat.entry, secondBeat.exit, now), secondBeat.exit, false);
    later.third(complete(1, thirdBeat.entry, thirdBeat.exit, now), thirdBeat.exit, false);
    later.fourth(
      complete(2, fourthBeat.entry, fourthBeat.exit, now),
      fourthBeat.exit,
      complete(3, frame.logo, fourthBeat.exit, now),
      false,
    );
    presentation.render({
      y,
      firstExit: firstBeat.exit,
      first: travelFirst(),
      step: galleryStep(),
      gap: pacing.gap,
      frame,
      wipeBands,
      gallery,
    });
    physical.active(
      later.settled &&
        y >= markRegion.top &&
        y < markRegion.top + markRegion.height &&
        !document.hidden,
    );
    globe.approach(y >= locationRegion.top - locationLead - innerHeight);
    globe.active(
      globeActive && y >= locationRegion.top && y < locationRegion.top + locationRegion.height,
    );
    save();
  }
  window.addEventListener('scroll', schedule, { ...options, passive: true });
  // Both directions traverse the same measured stops, including section handoffs.
  // Fine trackpad deltas, native touch and keys retain native scrolling.
  function resizeSurfaces() {
    measurements.resizeSurface();
    film?.resize(true);
    schedule();
  }
  const resized = () => {
    if (viewport.refresh()) {
      const region = regions.find((r) => scrollY >= r.top && scrollY < r.top + r.height);
      const progress = region ? (scrollY - region.top) / Math.max(1, region.height) : 0;
      measure();
      if (region) scrollTo({ top: region.top + progress * region.height, behavior: 'instant' });
      filmPacing.reset();
    } else resizeSurfaces();
  };
  window.addEventListener('resize', resized, { ...options, passive: true });
  document.fonts.addEventListener('loadingdone', () => measure(), options);
  window.visualViewport?.addEventListener('resize', resizeSurfaces, { ...options, passive: true });
  touch.addEventListener('change', () => measure(), options);
  window.addEventListener(
    'pointermove',
    (e) => {
      if (
        document.documentElement.dataset.menuOpen !== 'true' &&
        scrollY >= galleryRegion.top &&
        scrollY < galleryRegion.top + galleryRegion.height &&
        e.pointerType !== 'touch' &&
        !reduced.matches
      )
        gallery.orient((e.clientX / innerWidth) * 2 - 1, (e.clientY / innerHeight) * 2 - 1);
    },
    options,
  );
  function navigate(hash: string, push = false) {
    if (!['#work', '#writing', '#contact'].includes(hash)) return false;
    if (!readiness.complete) {
      pendingDestination = hash;
      return false;
    }
    positionRevision++;
    historyState.cancel();
    scrolling.cancel();
    filmHandoff.skip();
    filmPacing.reset();
    if (push && location.hash !== hash) {
      save(true);
      history.pushState(history.state, '', hash);
    }
    handledHash = hash;
    if (hash !== '#contact') {
      gallery.select(hash === '#writing' ? 'articles' : 'projects');
      travel.resetEntry(gallery.count);
    }
    viewport.refresh();
    measure();
    const target = root.querySelector<HTMLElement>(hash)!;
    const top = reduced.matches
      ? target.getBoundingClientRect().top + scrollY
      : hash === '#contact'
        ? locationRegion.top + Math.min(viewport.height, locationRegion.height - viewport.height)
        : travelFirst() + galleryStep() * 3;
    scrollTo({ top: Math.round(top), behavior: 'instant' });
    cancelAnimationFrame(raf);
    update(performance.now());
    target.focus({ preventScroll: true });
    save(true);
    return true;
  }
  window.addEventListener(
    'hashchange',
    () => {
      if (location.hash === handledHash) return;
      pendingDestination = location.hash;
      schedule();
    },
    options,
  );
  window.addEventListener(
    'popstate',
    () => {
      handledHash = location.hash;
      pendingDestination = '';
      const category = history.state?.homepageV2?.category;
      if (typeof category === 'string') gallery.select(category);
      historyState.pageshow(true);
      if (historyState.restoring) restorePosition();
      else {
        pendingDestination = location.hash;
        schedule();
      }
    },
    options,
  );
  document.addEventListener(
    'click',
    (e) => {
      const anchor = (e.target as Element).closest<HTMLAnchorElement>('a[href^="#"]');
      if (
        !anchor ||
        e.defaultPrevented ||
        e.button !== 0 ||
        e.ctrlKey ||
        e.metaKey ||
        e.shiftKey ||
        e.altKey ||
        anchor.target === '_blank' ||
        anchor.hasAttribute('download')
      )
        return;
      if (navigate(anchor.hash, true)) {
        e.preventDefault();
      }
    },
    options,
  );
  reduced.addEventListener(
    'change',
    () => {
      root.dataset.reduced = String(reduced.matches);
      filmPacing.reset();
      filmHandoff.skip();
      measure();
    },
    options,
  );
  document.addEventListener(
    'visibilitychange',
    () => {
      suspended = document.hidden;
      if (suspended) {
        entrances.forEach((entrance) => entrance.suspend());
        scrolling.cancel();
        film.suspend();
        physical.active(false);
      } else {
        film.resume();
        schedule();
      }
    },
    options,
  );
  window.addEventListener(
    'pagehide',
    (e) => {
      scrolling.cancel();
      save(true);
      suspended = true;
      entrances.forEach((entrance) => entrance.suspend());
      film.suspend();
      physical.active(false);
      if (!e.persisted) dispose();
    },
    options,
  );
  window.addEventListener(
    'pageshow',
    (e) => {
      historyState.pageshow(e.persisted);
      if (e.persisted) {
        suspended = false;
        film.resume();
        readiness.hide();
        measure();
      }
      restorePosition();
    },
    options,
  );
  observer.observe(root);
  observer.observe(environment);
  root.dataset.reduced = String(reduced.matches);
  measure();
  function dispose() {
    if (disposed) return;
    disposed = true;
    lifetime.abort();
    scope.dispose();
  }
  return dispose;
}
