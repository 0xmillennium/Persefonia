import coastCompact from "./media/home/coast-720.mp4?url";
import coastWide from "./media/home/coast-1280.mp4?url";
import { filmProgress } from "./home-film";

/** Optional media enhancement. Native HTML owns every public action and fact. */
export function enhanceHomepage(): () => void {
  const lifetime = new AbortController();
  const options = { signal: lifetime.signal };
  const menu = document.querySelector<HTMLDetailsElement>(".home-nav__mobile");
  const summary = menu?.querySelector("summary");
  menu?.addEventListener("click", event => {
    const link = (event.target as Element).closest<HTMLAnchorElement>("a[href^='#']");
    if (!link) return;
    menu.open = false;
    document.getElementById(link.hash.slice(1))?.focus({ preventScroll: true });
  }, options);
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && menu?.open) {
      menu.open = false;
      summary?.focus();
    }
  }, options);
  document.addEventListener("click", event => {
    if (menu?.open && event.target instanceof Node && !menu.contains(event.target)) menu.open = false;
  }, options);

  const cinema = document.querySelector<HTMLElement>("[data-home-film]");
  const scene = document.querySelector<HTMLElement>(".home-identity");
  const image = cinema?.querySelector<HTMLElement>(".home-cinema__image");
  const video = cinema?.querySelector<HTMLVideoElement>("[data-home-video]");
  const toggle = cinema?.querySelector<HTMLButtonElement>("[data-motion-toggle]");
  if (!scene || !cinema || !image || !video || !toggle) return () => lifetime.abort();

  let resizeObserver: ResizeObserver | undefined;
  let frame = 0;
  let disposed = false;
  let suspended = false;
  let userPaused = false;
  let loaded = false;
  let failed = false;
  let hasDecoded = false;
  let interacted = window.scrollY > 0;
  let target = 0;
  let loadDeadline = 0;
  let shortLandscape: MediaQueryList;
  let reducedMotion: MediaQueryList;
  try {
    shortLandscape = window.matchMedia("(max-height: 600px) and (min-aspect-ratio: 1/1)");
    reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  } catch (error) {
    lifetime.abort();
    throw error;
  }
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;

  const spatial = () => !reducedMotion.matches && !connection?.saveData && !shortLandscape.matches;
  const active = () => !disposed && !suspended && !document.hidden && !failed && !userPaused && !reducedMotion.matches && !connection?.saveData && !shortLandscape.matches;
  const cancelFrames = () => {
    window.cancelAnimationFrame(frame);
    frame = 0;
  };
  const stop = () => {
    cancelFrames();
    video.pause();
  };
  const dispose = () => {
    if (disposed) return;
    disposed = true;
    stop();
    lifetime.abort();
    resizeObserver?.disconnect();
    window.clearTimeout(loadDeadline);
    delete scene.dataset.sceneEnhanced;
    cinema.style.removeProperty("--scene-progress");
    toggle.hidden = true;
    delete cinema.dataset.filmState;
    video.removeAttribute("src");
    video.load();
  };
  const fail = () => {
    failed = true;
    stop();
    cinema.dataset.filmState = "failed";
    window.clearTimeout(loadDeadline);
    delete scene.dataset.sceneEnhanced;
    cinema.style.removeProperty("--scene-progress");
    toggle.hidden = true;
    video.removeAttribute("src");
    video.load();
  };
  const markDecoded = () => {
    // A hidden video may not receive compositor frame callbacks. Readiness is
    // established by the decoder's loaded/seeked state, then verified in pixels.
    if (active() && !video.seeking && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA
        && Math.abs(video.currentTime - target) <= 1 / 30) {
      hasDecoded = true;
      window.clearTimeout(loadDeadline);
      cinema.dataset.filmState = "ready";
      toggle.hidden = false;
    }
  };

  const update = () => {
    frame = 0;
    if (!active()) return;
    const rect = image.getBoundingClientRect();
    if (rect.bottom <= 0 || rect.top >= window.innerHeight) return;
    const geometry = scene.getBoundingClientRect();
    const progress = filmProgress(geometry.top, geometry.height, window.scrollY, cinema.getBoundingClientRect().height);
    cinema.style.setProperty("--scene-progress", progress.toFixed(5));
    if (!interacted || connection?.saveData) return;
    if (!loaded) {
      loaded = true;
      // Choose once. Resizing never restarts the film or downloads another copy.
      video.src = window.matchMedia("(min-width: 1060px)").matches ? coastWide : coastCompact;
      video.preload = "auto";
      video.load();
      loadDeadline = window.setTimeout(() => { if (!hasDecoded) fail(); }, 12000);
    }
    if (!Number.isFinite(video.duration) || video.duration <= 0) return;
    target = progress * Math.max(0, video.duration - 1 / 24);
    if (!video.seeking && Math.abs(video.currentTime - target) > 1 / 30) {
      video.currentTime = target;
    } else if (!video.seeking) markDecoded();
  };
  const schedule = () => {
    if (!frame && active()) frame = window.requestAnimationFrame(update);
  };
  const refreshMotion = () => {
    stop();
    if (failed || disposed) return;
    const isStatic = !spatial();
    if (isStatic) {
      delete scene.dataset.sceneEnhanced;
      cinema.style.removeProperty("--scene-progress");
    } else scene.dataset.sceneEnhanced = "";
    // Pausing retains a decoded scene; preference-based fallbacks use the poster.
    const isReady = hasDecoded && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && !video.seeking;
    cinema.dataset.filmState = isStatic ? "static"
      : userPaused && hasDecoded ? "paused"
      : isReady ? "ready" : "loading";
    toggle.hidden = isStatic || !hasDecoded;
    toggle.textContent = userPaused ? toggle.dataset.resumeLabel ?? "Resume motion" : toggle.dataset.pauseLabel ?? "Pause motion";
    if (!isStatic && !userPaused) schedule();
  };

  try {
    toggle.addEventListener("click", () => { userPaused = !userPaused; refreshMotion(); }, options);
    window.addEventListener("scroll", () => { interacted = true; schedule(); }, { ...options, passive: true });
    window.addEventListener("resize", schedule, { ...options, passive: true });
    video.addEventListener("loadeddata", schedule, options);
    video.addEventListener("seeked", () => {
      markDecoded();
      // Latest target wins while a prior seek is in flight; no perpetual loop.
      if (Math.abs(video.currentTime - target) > 1 / 30) schedule();
    }, options);
    video.addEventListener("error", fail, options);
    reducedMotion.addEventListener("change", refreshMotion, options);
    shortLandscape.addEventListener("change", refreshMotion, options);
    document.addEventListener("visibilitychange", () => { if (document.hidden) stop(); else schedule(); }, options);
    window.addEventListener("pagehide", event => {
      suspended = true;
      stop();
      if (!event.persisted) dispose();
    }, options);
    window.addEventListener("pageshow", () => {
      suspended = false;
      // A BFCache restore can bring a different preference and scroll geometry.
      refreshMotion();
    }, options);
    if (typeof ResizeObserver !== "undefined") {
      resizeObserver = new ResizeObserver(schedule);
      resizeObserver.observe(scene);
      resizeObserver.observe(cinema);
    }
    refreshMotion();
    return dispose;
  } catch (error) {
    dispose();
    throw error;
  }
}
