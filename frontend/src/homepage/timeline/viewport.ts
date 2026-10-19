/** Stable story geometry is independent of the browser's retracting toolbars. */
export function storyViewport(root: HTMLElement) {
  const probe = document.createElement('div');
  probe.className = 'home-viewport-probe';
  probe.setAttribute('aria-hidden', 'true');
  root.append(probe);
  let width = probe.clientWidth,
    height = probe.clientHeight;
  const publish = () => root.style.setProperty('--story-viewport-height', `${height}px`);
  publish();
  return {
    get width() {
      return width;
    },
    get height() {
      return height;
    },
    refresh() {
      const w = probe.clientWidth,
        h = probe.clientHeight;
      if (w === width && h === height) return false;
      width = w;
      height = h;
      publish();
      return true;
    },
    dispose() {
      probe.remove();
      root.style.removeProperty('--story-viewport-height');
    },
  };
}
