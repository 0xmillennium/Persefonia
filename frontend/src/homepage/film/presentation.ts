import { Renderer, type Scene } from './rendering/renderer';
import type { Hold } from './hold/state';
import type { HoldMaterial } from './hold/material';
import { applyHeroOpening, filmCoverBox, heroOpening, type HeroWritingBounds } from './cutouts';
import {
  isScrollFilm,
  isLoopBackground,
  nextBackground,
  type Background,
  type BackgroundKey,
  type BackgroundTransport,
} from './backgrounds';
import type { ScrollFilms } from './scroll-films';
import type { ResponsiveLoop } from './responsive-loop';

type PosterPose = 'opening' | 'final';
interface PresentationFrame {
  transport: BackgroundTransport;
  hold: Hold;
  material: HoldMaterial;
  fluid: Scene<Background>['field'];
  width: number;
  height: number;
  expansion: number;
  writingBounds: HeroWritingBounds;
  reduced: boolean;
  filmVisible: boolean;
  opacity: number;
  now: number;
  fluidChanged: boolean;
  holdChanged: boolean;
  uploads: number | undefined;
}

/** Selects native video/poster or GPU presentation and owns its visual resources. */
export class FilmPresentation {
  private renderer: Renderer<BackgroundKey, Background> | null = null;
  private needsDraw = true;
  private surfacePainted = false;
  private sheetGeneration = 0;
  private sheetSource = '';
  private sheetOpening = '';
  private disposed = false;
  private sheetImage: HTMLImageElement;
  private sheet: HTMLElement;
  private status: HTMLElement;
  private notice: HTMLOutputElement;
  private countdown: HTMLElement;
  constructor(
    private root: HTMLElement,
    private canvas: HTMLCanvasElement,
    private video: ScrollFilms,
    private loop: ResponsiveLoop,
    private wake: () => void,
  ) {
    this.sheetImage = root.querySelector<HTMLImageElement>('[data-hero-image]')!;
    this.sheet = root.querySelector<HTMLElement>('.home-cinema__image')!;
    this.status = root.querySelector<HTMLElement>('[data-film-status]')!;
    this.notice = root.querySelector<HTMLOutputElement>('[data-film-notice]')!;
    this.countdown = root.querySelector<HTMLElement>('[data-hold-countdown]')!;
  }
  get available() {
    return !!this.renderer;
  }
  get fluid() {
    return this.renderer?.fluid;
  }
  get uploads() {
    return this.renderer?.uploads;
  }
  invalidate() {
    this.needsDraw = true;
  }
  initialize(width: number, height: number, reduced: boolean) {
    this.disable();
    this.needsDraw = true;
    this.surfacePainted = false;
    this.sheetSource = '';
    if (!reduced) {
      this.renderer = new Renderer<BackgroundKey, Background>(this.canvas, false, true);
      this.renderer.resize(width, height);
    }
    this.canvas.hidden = !this.renderer;
  }
  disable() {
    this.renderer?.dispose();
    this.renderer = null;
    this.canvas.hidden = true;
    delete this.root.dataset.gpuSheet;
  }
  resize(width: number, height: number) {
    this.renderer?.resize(width, height);
    this.invalidate();
  }
  swap() {
    this.renderer?.swap();
  }
  key(film: Background, desired: number, pose: PosterPose): BackgroundKey {
    return isScrollFilm(film)
      ? (this.video.presentation(film, desired, pose)?.key ?? `${film}:${desired}`)
      : `${film}:${this.loop.revision}`;
  }
  source(film: Background, desired: number, pose: PosterPose) {
    return isScrollFilm(film)
      ? this.video.presentation(film, desired, pose)?.source
      : this.loop.image;
  }
  prepare(transport: BackgroundTransport, hold: Hold, fluidActive: boolean, pose: PosterPose) {
    const { video, renderer } = this;
    const activeKey = this.key(transport.film, transport.desired, pose);
    const nextFilm = nextBackground(transport.film);
    const nextKey = this.key(nextFilm, transport.desired, pose);
    const active = this.source(transport.film, transport.desired, pose);
    const next = hold.preparing ? this.source(nextFilm, transport.desired, pose) : undefined;
    const paired = !!active && !!next;
    if (
      renderer &&
      (fluidActive ||
        hold.animating ||
        (isScrollFilm(transport.film) && video.failed(transport.film)))
    ) {
      if (active && (hold.phase !== 'REVEALING' || paired)) renderer.upload(0, activeKey, active);
      if (paired) renderer.upload(1, nextKey, next!);
    }
    return paired;
  }
  async loadSheet() {
    const target = this.renderer;
    if (!target || this.sheetSource === this.sheetImage.currentSrc) return;
    const generation = ++this.sheetGeneration;
    try {
      await this.sheetImage.decode();
      const source = this.sheetImage.currentSrc;
      const bitmap = await createImageBitmap(this.sheetImage);
      try {
        if (this.disposed || this.renderer !== target || generation !== this.sheetGeneration)
          return;
        target.setSheet(bitmap);
        this.sheetSource = source;
        this.needsDraw = true;
        this.wake();
      } finally {
        bitmap.close();
      }
    } catch {
      /* The DOM photo and its static cutout remain the fallback. */
    }
  }
  render({
    transport,
    hold,
    material,
    fluid,
    width,
    height,
    expansion,
    writingBounds,
    reduced,
    filmVisible,
    opacity,
    now,
    fluidChanged,
    holdChanged,
    uploads,
  }: PresentationFrame): boolean {
    const { renderer, video, loop, canvas, root } = this;
    const posterPose = () => (expansion < 1 ? ('opening' as const) : ('final' as const));
    const key = this.key(transport.film, transport.desired, posterPose());
    const bounds = filmCoverBox(width, height, transport.film);
    const nativeAvailable = isScrollFilm(transport.film)
      ? video.hasVisual(transport.film, transport.desired, posterPose())
      : !!loop.image;
    const native = nativeAvailable && (reduced || !renderer || (!fluid.active && !hold.animating));
    if (native) {
      video.show(
        isScrollFilm(transport.film) ? transport.film : null,
        transport.desired,
        posterPose(),
        bounds,
        filmVisible ? opacity : 0,
      );
      loop.show(isLoopBackground(transport.film), bounds, filmVisible ? opacity : 0);
      canvas.style.opacity = '0';
      this.surfacePainted = false;
      delete root.dataset.gpuSheet;
      this.needsDraw = true;
      return (
        !!this.source(transport.film, transport.desired, posterPose()) && transport.present(key)
      );
    } else {
      if (renderer?.residentKeys[0] === key) canvas.style.opacity = '';
      const draw =
        (this.surfacePainted || renderer?.residentKeys[0] === key) &&
        (this.needsDraw ||
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
          reduced: reduced,
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
        this.needsDraw = false;
        this.surfacePainted = true;
      }
      if (submitted && renderer?.residentKeys[0] === key) {
        video.show(null, transport.desired, posterPose(), bounds, 0);
        loop.show(false, bounds, 0);
        canvas.hidden = false;
        transport.present(key);
        // The gallery curtain covers this entire environment. Keep
        // fluid state alive underneath so a quick reversal reveals it.
        if (renderer.hasSheet) root.dataset.gpuSheet = 'true';
        return true;
      }
    }
    return false;
  }
  photo(
    transport: BackgroundTransport,
    width: number,
    height: number,
    expansion: number,
    writingBounds: HeroWritingBounds,
  ) {
    const openingKey = `${transport.film}:${width}:${height}:${expansion}:${JSON.stringify(writingBounds)}`;
    if (this.sheetOpening !== openingKey) {
      applyHeroOpening(
        this.sheet,
        width,
        height,
        heroOpening(width, height, expansion, transport.film, writingBounds),
      );
      this.sheetOpening = openingKey;
    }
  }
  describe(transport: BackgroundTransport, hold: Hold, reduced: boolean, expansion: number) {
    const { root, video, loop } = this;
    const { status, notice, countdown } = this;
    const posterPose = () => (expansion < 1 ? ('opening' as const) : ('final' as const));
    const displayed = transport.presented?.split(':');
    const frameLabel = isLoopBackground(transport.film)
      ? 'Waterfall · loop'
      : displayed
        ? `${displayed[0] === 'tree' ? 'Tree' : 'Robotic arm'} · ${String(Number(displayed[1]) + 1).padStart(3, '0')} / 192`
        : 'Preparing first film frame';
    const scrollPresentation = isScrollFilm(transport.film)
      ? video.presentation(transport.film, transport.desired, posterPose())
      : undefined;
    const staticMessage =
      scrollPresentation?.kind === 'poster'
        ? `${transport.film === 'tree' ? 'Tree' : 'Robotic arm'} · static ${scrollPresentation.key.endsWith(':opening') ? 'opening' : 'final'} poster.${reduced || !isScrollFilm(transport.film) || !video.failed(transport.film) ? '' : ' Select the film again to retry.'}`
        : '';
    const failureMessage =
      hold.failure ??
      (isLoopBackground(transport.film) && (loop.failed || loop.blocked)
        ? 'Waterfall · playback unavailable; still frame retained. Hold the background or select the film again to retry.'
        : isScrollFilm(transport.film) &&
            video.unavailable(transport.film, posterPose()) &&
            !scrollPresentation
          ? 'Film unavailable. Content and navigation remain available; select the film again to retry.'
          : '');
    const message = failureMessage || staticMessage || frameLabel;
    root.dataset.filmPresentation =
      scrollPresentation?.kind ??
      (isLoopBackground(transport.film) && loop.image
        ? !reduced && loop.videoReady
          ? 'video'
          : 'poster'
        : isScrollFilm(transport.film) && video.unavailable(transport.film, posterPose())
          ? 'unavailable'
          : 'loading');
    if (status.textContent !== message) status.textContent = message;
    const announcement = failureMessage || (!reduced ? staticMessage : '');
    if (notice.textContent !== announcement) notice.textContent = announcement;
    countdown.hidden = hold.countdown === null;
    if (hold.countdown !== null) {
      countdown.textContent = String(hold.countdown);
      countdown.style.transform = `translate(${hold.center.x}px,${hold.center.y}px)`;
    }
  }
  dispose() {
    this.disposed = true;
    this.disable();
    this.sheetGeneration++;
    delete this.root.dataset.filmPresentation;
    this.root.querySelector<HTMLOutputElement>('[data-film-notice]')!.textContent = '';
    for (const name of ['clip-path', 'mask-image', 'mask-composite'])
      this.sheet.style.removeProperty(name);
  }
}
