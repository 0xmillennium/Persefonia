import { test as base, expect } from '@playwright/test';

/** Failure capture has independent budgets; a capture error never replaces the test's error. */
export const test = base.extend<{ failureEvidence: void }>({
  failureEvidence: [
    async ({ page, context }, use, info) => {
      await context.tracing.start({ screenshots: true, snapshots: true, sources: true });
      await use();
      const failed = info.status !== info.expectedStatus;
      const errors: string[] = [];
      const bounded = async (
        label: string,
        action: () => Promise<unknown>,
        milliseconds: number,
      ) => {
        let timer: ReturnType<typeof setTimeout> | undefined;
        try {
          await Promise.race([
            action(),
            new Promise((_, reject) => {
              timer = setTimeout(
                () => reject(new Error(`${label} deadline exceeded`)),
                milliseconds,
              );
            }),
          ]);
        } catch (error) {
          errors.push(`${label}: ${String(error)}`);
        } finally {
          clearTimeout(timer);
        }
      };
      if (failed) {
        await bounded(
          'hold prerequisite metadata',
          async () => {
            const metadata = await page.evaluate(() => {
              const observer = window.__holdObserver;
              const root = document.querySelector<HTMLElement>('[data-home-v2]');
              return {
                observer: observer && {
                  ...observer,
                  mask: observer.mask && {
                    context: observer.mask.context,
                    texture: observer.mask.texture,
                    sequence: observer.mask.sequence,
                    action: observer.mask.action,
                    width: observer.mask.width,
                    height: observer.mask.height,
                  },
                },
                presentation: root && {
                  film: root.dataset.film,
                  ready: root.dataset.ready,
                  gpuSheet: root.dataset.gpuSheet,
                  presentation: root.dataset.filmPresentation,
                },
                media: [...document.querySelectorAll('video')].map((video) => ({
                  readyState: video.readyState,
                  seeking: video.seeking,
                  paused: video.paused,
                  width: video.videoWidth,
                  height: video.videoHeight,
                  // Retain only the self-hosted delivery path, never arbitrary URLs or query data.
                  source: new URL(video.currentSrc || video.src || '/', location.href).pathname,
                })),
              };
            });
            await info.attach('hold-prerequisites', {
              body: JSON.stringify(metadata),
              contentType: 'application/json',
            });
          },
          1000,
        );
        await bounded('resume virtual clock', () => page.clock.resume(), 1000);
        await bounded(
          'screenshot',
          () =>
            page.screenshot({
              path: info.outputPath('failure.png'),
              timeout: 4000,
              animations: 'disabled',
            }),
          5000,
        );
      }
      await bounded(
        'trace finalization',
        () => context.tracing.stop(failed ? { path: info.outputPath('trace.zip') } : {}),
        5000,
      );
      if (failed)
        await bounded(
          'trace attachment',
          () =>
            info.attach('trace', {
              path: info.outputPath('trace.zip'),
              contentType: 'application/zip',
            }),
          1000,
        );
      if (errors.length)
        await info.attach('diagnostic-errors', {
          body: JSON.stringify({
            primary: info.errors.map((error) => error.message),
            diagnosticErrors: errors,
          }),
          contentType: 'application/json',
        });
    },
    { auto: true, timeout: 15_000 },
  ],
});
export { expect };
export type { Page } from '@playwright/test';
