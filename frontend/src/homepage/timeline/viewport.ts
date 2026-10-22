/** Stable story geometry is independent of the browser's retracting toolbars. */
export function storyViewport(root: HTMLElement) {
  const createProbe = (className: string) => {
    const node = document.createElement('div');
    node.className = className;
    node.setAttribute('aria-hidden', 'true');
    root.append(node);
    return node;
  };
  const probe = createProbe('home-viewport-probe');
  const composition = createProbe('home-composition-probe');
  let width = probe.clientWidth,
    height = probe.clientHeight;
  const publish = () => {
    root.style.setProperty('--story-viewport-height', `${height}px`);
    root.style.setProperty(
      '--composition-viewport-height',
      `${Math.max(height, composition.clientHeight)}px`,
    );
  };
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
      composition.remove();
      root.style.removeProperty('--story-viewport-height');
      root.style.removeProperty('--composition-viewport-height');
    },
  };
}
