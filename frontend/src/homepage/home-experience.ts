import { homepageReadiness } from './readiness';
import { GalleryHandoff } from './gallery/handoff';
import { homepageHistory } from './timeline/history';
import { measureRegions, type SceneGeometry } from './timeline/geometry';
import { sceneState } from './timeline/scene-state';
import { DisposalScope } from '../shared/disposal';
import { focusNavigation } from '../shared/focus';
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
  regionProgress,
  windowProgress,
  filmRange,
  writingPacing,
  writingBeat,
  footerPhase,
  CURTAIN_START,
  HOMEPAGE_SCROLL_SPEED,
  GALLERY_HANDOFF,
} from './timeline/pacing';
import { navigationCover } from '../navigation/cover';
import { scrollNavigation } from './timeline/scroll-navigation';
import { galleryTravelProgress } from './gallery/motion';
import { firstWriting } from './writing/first';
import { secondWriting } from './writing/second';
import { galleryWriting } from './gallery/writing';
import { laterWriting, galleryCurtain } from './writing/later';
/** Native scroll owns choreography. Every region is measured independently. */
export function enhanceHomepage(): () => void {
  const scope = new DisposalScope();
  try {
    return mountHomepage(scope);
  } catch (error) {
    scope.dispose();
    throw error;
  }
}
function mountHomepage(scope: DisposalScope): () => void {
  const found = document.querySelector<HTMLElement>('[data-home-v2]');
  if (!found) return () => {};
  const root: HTMLElement = found;
  const lifetime = new AbortController(),
    options = { signal: lifetime.signal };
  const historyState = homepageHistory();
  scope.add(() => historyState.dispose());
  const pacing = writingPacing();
  const distances = {
    '--first-writing-distance': pacing.hero,
    '--statement-writing-distance': pacing.statement,
    '--writing-distance': pacing.body,
    '--writing-gap': pacing.gap,
  };
  const clearDistances = () => {
    for (const property of Object.keys(distances)) root.style.removeProperty(property);
  };
  scope.add(() => {
    lifetime.abort();
    clearDistances();
    delete root.dataset.enhanced;
    delete root.dataset.film;
    delete root.dataset.filmBackdrop;
    root.querySelectorAll<HTMLElement>('.scene-face').forEach((face) => {
      face.inert = false;
      delete face.dataset.visible;
      for (const property of [
        'transform',
        'clip-path',
        'z-index',
        '--local-progress',
        '--gallery-exit',
        '--gallery-entry-opacity',
      ])
        face.style.removeProperty(property);
    });
  });
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const measuredSizes = new WeakMap<Element, string>();
  const sizeOf = (node: Element) => `${node.clientWidth}:${node.clientHeight}`;
  // Explicit gallery measurements already include their own layout writes.
  const observer = new ResizeObserver((entries) => {
    if (entries.some((entry) => measuredSizes.get(entry.target) !== sizeOf(entry.target)))
      measure();
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
  let curtainCount = curtainBars.length;
  let curtainHeight = innerHeight,
    galleryEntryDistance = 0;
  let disposed = false,
    raf = 0,
    suspended = false;
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
  let galleryClipLeft = 0,
    galleryClipTop = 0;
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
  const galleryPosition = (p: number) => travel.position(p);
  const galleryFirst = () => travel.first();
  const galleryStep = () => travel.step();
  const travelFirst = () => travel.travelFirst();
  const travelEnd = () => travel.end(gallery.count);
  const travelPosition = (p: number) => travel.travelPosition(p, gallery.count);
  const galleryCue = () =>
    Math.ceil(filmRange(filmRegion.top, filmRegion.height, markRegion.top, markRegion.height).end) +
    curtainHeight * GALLERY_HANDOFF.cue;
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
  let geometry: Readonly<Record<string, SceneGeometry>> = Object.freeze({});
  function measure(preserveGlide = false) {
    const measuredY = scrollY;
    if (!preserveGlide) scrolling.cancel();
    galleryEntryDistance = 0;
    galleryRegion.node.style.setProperty('--gallery-entry-distance', '0px');
    galleryRegion.node.style.setProperty('--gallery-tail-distance', '0px');
    const count = Math.max(
      ...[...root.querySelectorAll('.gallery-group')].map((g) => g.children.length),
      1,
    );
    galleryRegion.node.style.setProperty(
      '--gallery-distance',
      String((3 + (count + 8) * 1.4) / HOMEPAGE_SCROLL_SPEED),
    );
    if (reduced.matches) clearDistances();
    else
      for (const [property, distance] of Object.entries(distances))
        root.style.setProperty(property, `${distance}px`);
    const readRegions = () => {
      geometry = measureRegions(regions, scrollY);
    };
    readRegions();
    curtainHeight = innerHeight;
    if (!reduced.matches) {
      const playback = filmRange(
        filmRegion.top,
        filmRegion.height,
        markRegion.top,
        markRegion.height,
      );
      galleryEntryDistance = Math.max(
        0,
        Math.ceil(playback.end) +
          curtainHeight * GALLERY_HANDOFF.entryDistance -
          galleryRegion.top -
          galleryRegion.height * 0.12,
      );
      galleryRegion.node.style.setProperty('--gallery-entry-distance', `${galleryEntryDistance}px`);
      readRegions();
    }
    travel.measure(galleryRegion.top, galleryRegion.height, galleryEntryDistance);
    if (!reduced.matches) {
      const tail = Math.max(0, travelEnd() - galleryPosition(0.82));
      galleryRegion.node.style.setProperty('--gallery-tail-distance', `${tail}px`);
      readRegions();
      if (tail > 0 && scrollY < measuredY) scrollTo({ top: measuredY, behavior: 'instant' });
    }
    film?.resize();
    writing.measure();
    perspective.measure();
    const galleryBox = gallerySpace.getBoundingClientRect();
    galleryClipLeft = galleryBox.left;
    galleryClipTop = galleryBox.top;
    curtainCount = curtainBars.filter((bar) => getComputedStyle(bar).display !== 'none').length;
    for (const node of [root, environment]) measuredSizes.set(node, sizeOf(node));
    schedule();
    restorePosition();
  }
  function restorePosition() {
    historyState.restore(readiness.complete, (y) => {
      measure();
      scrollTo({ top: y, behavior: 'instant' });
      schedule();
    });
  }
  const readiness = homepageReadiness(root, loading, historyState.historyVisit, reduced, measure);
  scope.add(() => readiness.dispose());
  root.dataset.enhanced = 'true';
  const mobileMenu = mobileNavigation();
  scope.add(mobileMenu);
  film = mountFilm({
    root,
    ready: () => readiness.ready('film'),
    selectionChanged: schedule,
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
    // Keep the derived presentation flag consistent with the current query.
    // Scene wakeups and BFCache restoration reconcile it as well as change
    // notifications; stale derived state must not control measured geometry.
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
    const y = scrollY,
      firstBeat = writingBeat(y, filmRegion.top, true);
    writing.update(firstBeat.exit, reduced.matches);
    if (reduced.matches) {
      gallerySpace.style.removeProperty('clip-path');
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
      root.querySelector<HTMLElement>('[data-scene-wipe]')!.hidden = true;
      for (const region of regions) {
        region.face.dataset.visible = 'true';
        region.face.inert = false;
        for (const name of [
          'clip-path',
          'transform',
          'z-index',
          '--gallery-exit',
          '--gallery-entry-opacity',
        ])
          region.face.style.removeProperty(name);
        region.face
          .querySelectorAll<HTMLElement>('.home-overline,.hero-scroll')
          .forEach((node) => node.style.removeProperty('visibility'));
      }
      gallery.active(false);
      physical.active(false);
      globe.approach(y + innerHeight >= locationRegion.top - innerHeight);
      globe.active(
        y + innerHeight >= locationRegion.top && y < locationRegion.top + locationRegion.height,
      );
      save();
      return;
    }
    const frame = sceneState(y, geometry, {
      end: travelEnd(),
      boundary: galleryBoundary(1),
      cue: galleryCue(),
      first: travelFirst(),
      authoredFirst: galleryFirst(),
      step: galleryStep(),
      count: gallery.count,
      columns: curtainCount,
    });
    const { expansion, entrance, galleryExit } = frame;
    const galleryP = frame.galleryProgress;
    const coverPhase = entrance.phase;
    const wipeBands = galleryCurtain(coverPhase, curtainCount);
    const curtainSolid = entrance.darkness;
    const locationPhase = footerPhase(y, locationRegion.top, locationRegion.height, innerHeight);
    const locationLead = innerHeight * 0.1;
    galleryText.update(entrance.writing, windowProgress(galleryP, 0.83, 0.9), false);
    const globeActive = footer.update(locationPhase, false);
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
    const { second: secondBeat, third: thirdBeat, fourth: fourthBeat } = frame;
    if (y + innerHeight >= markRegion.top - innerHeight) physical.prepare();
    perspective.update(secondBeat.entry, secondBeat.exit, false);
    later.third(thirdBeat.entry, thirdBeat.exit, false);
    later.fourth(fourthBeat.entry, fourthBeat.exit, frame.logo, false);
    for (const region of regions) {
      const p = regionProgress(y, region.top, region.height);
      const lead =
        region.name === 'gallery'
          ? markRegion.height * 0.12
          : region.name === 'location'
            ? locationLead
            : region.name === 'mark'
              ? pacing.gap
              : 0;
      const visible = y >= region.top - lead && y < region.top + region.height;
      region.face.dataset.visible = String(visible);
      const unavailable =
        !visible ||
        (region.name === 'gallery' &&
          ((y < Math.round(travelFirst()) &&
            gallery.empty &&
            (gallery.count > 0 || entrance.opacity === 0)) ||
            galleryExit >= 0.5));
      if (unavailable && region.face.contains(document.activeElement)) {
        focusNavigation();
      }
      region.face.inert = unavailable;
      if (region.name === 'gallery') {
        gallery.update(galleryTravelProgress(y, travelFirst(), galleryStep(), gallery.count));
        gallery.active(!unavailable);
      }
      if (!visible) continue;
      region.face.style.setProperty('--local-progress', String(p));
      if (region.name === 'hero') {
        region.face
          .querySelectorAll<HTMLElement>('.home-overline,.hero-scroll')
          .forEach((node) => (node.style.visibility = firstBeat.exit > 0.12 ? 'hidden' : ''));
      }
      if (region.name === 'gallery') {
        region.face.style.setProperty('--gallery-entry-opacity', String(entrance.opacity));
        region.face.style.setProperty('--gallery-exit', String(galleryExit));
        region.face.style.clipPath = 'none';
        if (wipeBands.at(-1)! < 1) {
          const frontier = wipeBands.flatMap((p, i) => [
            `${(i * innerWidth) / curtainCount - galleryClipLeft}px ${(1 - p) * curtainHeight - galleryClipTop}px`,
            `${((i + 1) * innerWidth) / curtainCount - galleryClipLeft}px ${(1 - p) * curtainHeight - galleryClipTop}px`,
          ]);
          gallerySpace.style.clipPath = `polygon(0 100%,${frontier.join(',')},100% 100%)`;
          region.face.style.zIndex = '11';
        } else {
          gallerySpace.style.clipPath = 'none';
          region.face.style.zIndex = '11';
        }
      }
      if (region.name === 'location')
        region.face.style.transform = `translateY(${-Math.max(0, y - (region.top + region.height - innerHeight))}px)`;
    }
    physical.active(
      later.settled &&
        y >= markRegion.top &&
        y < markRegion.top + markRegion.height &&
        !document.hidden,
    );
    wipe.hidden = coverPhase <= CURTAIN_START;
    wipe.style.setProperty('--curtain-solid', String(curtainSolid));
    wipeBands.forEach((p, i) => {
      (wipe.children[i] as HTMLElement).style.transform = `translateY(${(1 - p) * 100}%)`;
    });
    globe.approach(y >= locationRegion.top - locationLead - innerHeight);
    globe.active(
      globeActive && y >= locationRegion.top && y < locationRegion.top + locationRegion.height,
    );
    save();
  }
  window.addEventListener('scroll', schedule, { ...options, passive: true });
  // Both directions traverse the same measured stops, including section handoffs.
  // Fine trackpad deltas, native touch and keys retain native scrolling.
  window.addEventListener('resize', () => measure(), { ...options, passive: true });
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
  document.addEventListener(
    'click',
    (e) => {
      const anchor = (e.target as Element).closest<HTMLAnchorElement>('a[href^="#"]');
      if (!anchor) return;
      if (anchor.hash === '#work' || anchor.hash === '#writing') {
        gallery.select(anchor.hash === '#writing' ? 'articles' : 'projects');
        travel.resetEntry(gallery.count);
        measure();
        e.preventDefault();
        scrolling.cancel();
        historyState.cancel();
        window.scrollTo({ top: travelFirst(), behavior: 'instant' });
        // Make the destination available before transferring keyboard focus.
        cancelAnimationFrame(raf);
        update(performance.now());
        root.querySelector<HTMLElement>(anchor.hash)?.focus({ preventScroll: true });
      }
    },
    options,
  );
  reduced.addEventListener(
    'change',
    () => {
      root.dataset.reduced = String(reduced.matches);
      measure();
    },
    options,
  );
  document.addEventListener(
    'visibilitychange',
    () => {
      suspended = document.hidden;
      if (suspended) {
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
