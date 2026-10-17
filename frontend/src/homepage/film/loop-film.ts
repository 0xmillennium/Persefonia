import { loopVersion } from 'virtual:home-media';
import { loadBitmap } from './loader';
import { FILM_READINESS_TIMEOUT, type LoopTier } from './delivery';

/** Native playback at rest; decoded frames wake the shader only while it is used. */
export class LoopFilm {
  private video: HTMLVideoElement | null = null;
  private poster: HTMLImageElement | null = null;
  private bitmap: ImageBitmap | null = null;
  private abort = new AbortController();
  private disposed = false;
  private wanted = false;
  private watching = false;
  private frame = 0;
  private nativeFrames = false;
  private previousFrame = -1;
  private pending = false;
  private playIntent = 0;
  private attempt = 0;
  private deadline = 0;
  private playbackDeadline = 0;
  private suspended = false;
  revision = 0;
  blocked = false;
  failed = false;
  constructor(
    private parent: HTMLElement,
    readonly tier: LoopTier,
    private changed: () => void,
  ) {}
  private url(extension: string) {
    return `/assets/home-loops/${loopVersion}/${this.tier}/waterfall.${extension}`;
  }
  get posterUrl() {
    return this.url('webp');
  }
  get prepared() {
    return !!this.video;
  }
  get videoReady() {
    return (
      !!this.video &&
      !this.failed &&
      !this.video.error &&
      !this.video.seeking &&
      this.video.readyState >= 2
    );
  }
  get time() {
    return this.video?.currentTime ?? 0;
  }
  prepare(time = 0) {
    if (this.disposed || this.video) return;
    const video = (this.video = document.createElement('video'));
    video.className = 'film-presentation';
    video.dataset.loopVideo = '';
    video.loop = video.muted = video.playsInline = true;
    video.preload = 'auto';
    video.hidden = true;
    video.setAttribute('aria-hidden', 'true');
    video.poster = this.url('webp');
    const poster = (this.poster = new Image());
    poster.className = 'film-presentation';
    poster.alt = '';
    poster.hidden = true;
    poster.src = video.poster;
    this.parent.prepend(video, poster);
    void loadBitmap(video.poster, this.abort.signal)
      .then((image) => {
        if (this.disposed) {
          image.close();
          return;
        }
        this.bitmap = image;
        this.revision++;
        this.changed();
      })
      .catch(() => {
        /* A decoded video can still supply the presentation. */
      });
    const options = { signal: this.abort.signal };
    video.addEventListener(
      'loadedmetadata',
      () => {
        if (time > 0 && Number.isFinite(video.duration)) video.currentTime = time % video.duration;
      },
      options,
    );
    for (const name of ['loadeddata', 'seeked', 'playing', 'pause'] as const)
      video.addEventListener(
        name,
        () => {
          if (this.videoReady) this.clearDeadline();
          if (name === 'playing') this.clearPlaybackDeadline();
          this.revision++;
          this.changed();
        },
        options,
      );
    for (const name of ['waiting', 'stalled'])
      video.addEventListener(
        name,
        () => {
          if (this.wanted) this.waitForPlayback();
        },
        options,
      );
    let lastTime = 0;
    video.addEventListener(
      'timeupdate',
      () => {
        if (video.currentTime !== lastTime) this.clearPlaybackDeadline();
        lastTime = video.currentTime;
      },
      options,
    );
    video.addEventListener(
      'error',
      () => {
        this.fail();
      },
      options,
    );
    video.src = this.url('mp4');
    this.waitForVideo();
  }
  get image(): HTMLVideoElement | ImageBitmap | undefined {
    return this.videoReady ? this.video! : (this.bitmap ?? undefined);
  }
  setPlayback(allowed: boolean, observeFrames: boolean) {
    const video = this.video;
    const wanted = allowed && !this.blocked && !this.failed;
    if (video && wanted !== this.wanted) {
      this.wanted = wanted;
      const intent = ++this.playIntent;
      if (wanted && !this.pending) {
        this.pending = true;
        this.waitForPlayback();
        void video
          .play()
          .then(() => {
            this.clearPlaybackDeadline();
            this.pending = false;
            if (this.disposed || !this.wanted) video.pause();
            else if (intent !== this.playIntent && video.paused) this.wanted = false;
            this.changed();
          })
          .catch(() => {
            this.clearPlaybackDeadline();
            this.pending = false;
            // Cancellation is expected when leaving while play is pending.
            if (this.disposed) return;
            if (intent !== this.playIntent) {
              if (this.wanted) this.wanted = false;
              this.changed();
            } else if (this.wanted) {
              this.blocked = true;
              this.wanted = false;
              this.changed();
            }
          });
      } else if (!wanted) {
        this.clearPlaybackDeadline();
        video.pause();
      }
    }
    this.watch(wanted && observeFrames);
  }
  retry() {
    if (this.disposed || (!this.blocked && !this.failed)) return;
    this.blocked = false;
    this.wanted = false;
    if (this.failed && this.video) {
      this.failed = false;
      this.video.src = `${this.url('mp4')}?retry=${++this.attempt}`;
      this.video.load();
      this.waitForVideo();
    }
    this.changed();
  }
  private clearDeadline() {
    clearTimeout(this.deadline);
    this.deadline = 0;
  }
  private waitForVideo() {
    if (!this.deadline && !this.suspended && !this.failed && !this.videoReady)
      this.deadline = window.setTimeout(() => {
        this.clearDeadline();
        if (this.videoReady) this.changed();
        else this.fail();
      }, FILM_READINESS_TIMEOUT);
  }
  private clearPlaybackDeadline() {
    clearTimeout(this.playbackDeadline);
    this.playbackDeadline = 0;
  }
  private waitForPlayback() {
    if (this.playbackDeadline || this.suspended || this.failed || this.disposed) return;
    const time = this.video?.currentTime;
    this.playbackDeadline = window.setTimeout(() => {
      this.clearPlaybackDeadline();
      if (this.disposed || this.suspended || !this.wanted) return;
      if (this.videoReady && this.video?.currentTime !== time) {
        this.waitForPlayback();
        this.changed();
      } else this.fail();
    }, FILM_READINESS_TIMEOUT);
  }
  private fail() {
    if (this.disposed || this.failed) return;
    this.clearDeadline();
    this.clearPlaybackDeadline();
    this.failed = true;
    this.wanted = false;
    this.video?.pause();
    this.watch(false);
    this.changed();
  }
  suspend(value: boolean) {
    this.suspended = value;
    if (value) {
      this.clearDeadline();
      this.clearPlaybackDeadline();
    } else if (this.prepared) {
      this.waitForVideo();
      if (this.wanted && this.video?.paused) this.waitForPlayback();
    }
  }
  private watch(value: boolean) {
    if (value === this.watching) return;
    this.watching = value;
    if (!value) {
      if (this.frame) {
        if (this.nativeFrames) this.video?.cancelVideoFrameCallback(this.frame);
        else cancelAnimationFrame(this.frame);
      }
      this.frame = 0;
      return;
    }
    // A shader returning from native presentation must upload the current image.
    this.revision++;
    this.scheduleFrame();
  }
  private scheduleFrame() {
    const video = this.video;
    if (!video || !this.watching || this.disposed) return;
    const next = () => {
      this.frame = 0;
      this.revision++;
      this.changed();
      this.scheduleFrame();
    };
    this.nativeFrames = typeof video.requestVideoFrameCallback === 'function';
    if (this.nativeFrames) this.frame = video.requestVideoFrameCallback(next);
    else
      this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        const index = Math.floor(video.currentTime * 24);
        if (index !== this.previousFrame) {
          this.previousFrame = index;
          this.revision++;
          this.changed();
        }
        this.scheduleFrame();
      });
  }
  show(visible: boolean, bounds: readonly number[], opacity: number) {
    const videoReady = this.videoReady;
    for (const node of [this.video, this.poster]) {
      if (!node) continue;
      node.hidden = !visible || (node === this.video ? !videoReady : videoReady || !this.bitmap);
      if (node.hidden) continue;
      node.style.left = `${bounds[0]}px`;
      node.style.top = `${bounds[1]}px`;
      node.style.width = `${bounds[2]! - bounds[0]!}px`;
      node.style.height = `${bounds[3]! - bounds[1]!}px`;
      node.style.opacity = String(opacity);
    }
  }
  dispose() {
    this.disposed = true;
    this.clearDeadline();
    this.clearPlaybackDeadline();
    this.wanted = false;
    this.watch(false);
    this.abort.abort();
    if (this.video) {
      this.video.pause();
      this.video.removeAttribute('src');
      this.video.load();
      this.video.remove();
    }
    this.poster?.remove();
    this.bitmap?.close();
  }
}
