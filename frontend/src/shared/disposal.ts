/** One owner for completed resources, including partially initialized features. */
export class DisposalScope {
  private releases: Array<() => void> = [];
  private closed = false;

  add(release: () => void): void {
    if (this.closed) this.release(release);
    else this.releases.push(release);
  }

  dispose(): void {
    if (this.closed) return;
    this.closed = true;
    for (const release of this.releases.reverse()) this.release(release);
    this.releases = [];
  }

  private release(callback: () => void): void {
    try {
      callback();
    } catch (error) {
      console.error('Feature cleanup failed', error);
    }
  }
}
