/** Optional instrumentation installed by individual browser regression cases. */
export {};
declare global {
  interface Window {
    fallbackWrites?: string[];
    loopUploads?: number[];
    sourceRaf?: typeof requestAnimationFrame;
    sourceCallbacks?: FrameRequestCallback[];
    cancelPendingPlay?: () => void;
    violations?: string[];
  }
}
