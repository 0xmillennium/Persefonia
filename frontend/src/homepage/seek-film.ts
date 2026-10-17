import { type Film } from '../../experiments/scroll-film-navigator/src/transport';
import { videoVersion } from 'virtual:home-media';

interface Entry {
    video: HTMLVideoElement;
    desired: number;
    index: number;
    pending: number;
    failed: boolean;
    attempt: number;
}

export class SeekFilm {
    private entries = new Map<Film, Entry>();
    private abort = new AbortController();
    private disposed = false;
    constructor(private parent: HTMLElement, private tier: number, private changed: () => void) {}
    private source(film: Film, attempt: number) {
        return `/assets/home-videos/${videoVersion}/${this.tier}/${film}.mp4${attempt ? `?retry=${attempt}` : ''}`;
    }
    request(film: Film, index: number) {
        if (this.disposed) return;
        let entry = this.entries.get(film);
        if (!entry) {
            const video = document.createElement('video');
            video.className = 'film-fallback';
            video.muted = true;
            video.playsInline = true;
            video.preload = 'auto';
            video.hidden = true;
            video.setAttribute('aria-hidden', 'true');
            entry = { video, desired: index, index: -1, pending: -1, failed: false, attempt: 0 };
            this.entries.set(film, entry);
            const item = entry;
            const settled = () => {
                if (this.disposed || item.failed || video.seeking || video.readyState < 2) return;
                item.index = Math.min(191, Math.floor(video.currentTime * 30 + .0001));
                item.pending = -1;
                this.request(film, item.desired);
                this.changed();
            };
            video.addEventListener('loadeddata', settled, { signal: this.abort.signal });
            video.addEventListener('seeked', settled, { signal: this.abort.signal });
            video.addEventListener('error', () => {
                if (!this.disposed) { item.failed = true; this.changed(); }
            }, { signal: this.abort.signal });
            video.src = this.source(film, 0);
            this.parent.prepend(video);
        }
        entry.desired = index;
        if (!entry.failed && entry.video.readyState >= 1 && entry.pending !== index && (entry.index !== index || entry.video.seeking)) {
            entry.pending = index;
            entry.video.currentTime = (index + .25) / 30;
        }
    }
    get(film: Film, index: number) {
        const entry = this.entries.get(film);
        return entry && !entry.failed && !entry.video.seeking && entry.index === index ? entry.video : undefined;
    }
    failed(film: Film) { return this.entries.get(film)?.failed ?? false; }
    hasVisual(film: Film) { return (this.entries.get(film)?.index ?? -1) >= 0; }
    get hasError() { return [...this.entries.values()].some(entry => entry.failed); }
    retry() {
        for (const [film, entry] of this.entries) {
            if (!entry.failed) continue;
            entry.failed = false;
            entry.index = entry.pending = -1;
            entry.video.src = this.source(film, ++entry.attempt);
            entry.video.load();
        }
    }
    show(film: Film | null, bounds: readonly number[], opacity: number) {
        for (const [name, { video }] of this.entries) {
            video.hidden = film !== name;
            if (video.hidden) continue;
            video.style.left = `${bounds[0]}px`;
            video.style.top = `${bounds[1]}px`;
            video.style.width = `${bounds[2]! - bounds[0]!}px`;
            video.style.height = `${bounds[3]! - bounds[1]!}px`;
            video.style.opacity = String(opacity);
        }
    }
    dispose() {
        this.disposed = true;
        this.abort.abort();
        for (const { video } of this.entries.values()) { video.removeAttribute('src'); video.load(); video.remove(); }
        this.entries.clear();
    }
}
