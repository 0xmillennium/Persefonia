/** Readiness of the selected presentation, independent of loader animation. */
export interface MediaReadiness {
  state: 'loading' | 'video' | 'static' | 'fallback' | 'unavailable';
  progress: number;
}
