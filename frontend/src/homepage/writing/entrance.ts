import { clamp } from '../contracts/progress';

type State =
  'unentered' | 'entering' | 'presented' | 'reversing' | 'exiting' | 'suspended' | 'disposed';

/** Only entrance advances with time. Scene coordinates retain exclusive exit ownership. */
export class WritingEntrance {
  state: State = 'unentered';
  private progress = 0;
  private last: number | null = null;
  private exit = 0;
  private entry = 0;
  private reverse: { entry: number; progress: number } | null = null;
  constructor(
    private duration: number,
    private reverseEntry = false,
  ) {}

  sample(entry: number, exit: number, now: number, reduced = false): number {
    if (this.state === 'disposed') return this.progress;
    const previousExit = this.exit,
      previousEntry = this.entry;
    this.exit = exit;
    this.entry = entry;
    if (reduced || exit > 0) {
      this.reverse = null;
      this.progress = 1;
      this.state = reduced ? 'presented' : exit < previousExit ? 'reversing' : 'exiting';
    } else if (entry <= 0) {
      this.reverse = null;
      this.progress = 0;
      this.state = 'unentered';
    } else if (
      this.reverseEntry &&
      (entry < previousEntry - 1e-6 || (this.reverse && entry <= previousEntry + 1e-6))
    ) {
      // These owners depart backwards through their entrance slits. Anchor to
      // the visible pose so time-completed text never jumps to raw scroll progress.
      this.reverse ??= { entry: previousEntry, progress: this.progress };
      this.progress = this.reverse.progress * clamp(entry / this.reverse.entry);
      this.state = 'reversing';
    } else {
      this.reverse = null;
      const elapsed =
        this.last === null || this.state !== 'entering' ? 0 : Math.max(0, now - this.last);
      this.progress = clamp(Math.max(entry, this.progress + elapsed / this.duration));
      this.state = this.progress === 1 ? 'presented' : 'entering';
    }
    this.last = now;
    return this.progress;
  }
  get running() {
    return this.state === 'entering';
  }
  suspend() {
    this.last = null;
    if (this.state !== 'disposed') this.state = 'suspended';
  }
  dispose() {
    this.last = null;
    this.reverse = null;
    this.state = 'disposed';
  }
}
