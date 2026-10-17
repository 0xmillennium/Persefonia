import { pointerScale } from './pointer-scale';
export function pointerMarker(canHold: (target: Element) => boolean = () => false) {
  const abort = new AbortController(),
    options = { signal: abort.signal };
  const pointerOptions = { ...options, capture: true, passive: true };
  const fine = matchMedia('(any-hover: hover) and (any-pointer: fine)');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const forced = matchMedia('(forced-colors: active)');
  const clickable =
    'a[href],button,summary,[role="button"],[role="link"],input,select,label,.physical-identity svg,.location-globe canvas';
  const actionInputs = new Set([
    'button',
    'submit',
    'reset',
    'checkbox',
    'radio',
    'range',
    'color',
    'file',
    'image',
  ]);
  const marker = document.createElement('div');
  marker.className = 'home-pointer';
  marker.setAttribute('aria-hidden', 'true');
  marker.hidden = true;
  const dot = document.createElement('span');
  dot.className = 'home-pointer__dot';
  const hint = document.createElement('span');
  hint.className = 'home-pointer__hint';
  hint.lang = 'en';
  hint.textContent = 'Press and hold';
  hint.dataset.visible = 'false';
  marker.append(dot, hint);
  document.body.append(marker);
  let visible = false,
    pressed = false,
    hovered = false,
    hasPoint = false,
    captured = false,
    fineInput = false;
  let x = 0,
    y = 0,
    placedX = NaN,
    placedY = NaN;
  let frame = 0,
    refreshFrame = 0,
    scale = 1,
    targetScale = 1;
  let lastElement: Element | null = null;
  let hintTimer = 0,
    hintEligible = false;
  const hintAvailability = (available: boolean) => {
    if (available === hintEligible) return;
    hintEligible = available;
    clearTimeout(hintTimer);
    hintTimer = 0;
    hint.dataset.visible = 'false';
    if (available)
      hintTimer = window.setTimeout(() => {
        hintTimer = 0;
        const hit = document.elementFromPoint(x, y);
        if (visible && !pressed && !hovered && hit && canHold(hit)) hint.dataset.visible = 'true';
      }, 180);
  };
  let previousScaleTime = 0;
  const render = (now: number) => {
    frame = 0;
    if (!visible) return;
    if (x !== placedX || y !== placedY) {
      marker.style.transform = `translate3d(${x}px,${y}px,0)`;
      hint.classList.toggle('home-pointer__hint--left', x > innerWidth - 150);
      placedX = x;
      placedY = y;
    }
    const seconds = Math.min(0.05, Math.max(0, now - previousScaleTime) / 1000);
    previousScaleTime = now;
    scale = pointerScale(scale, targetScale, seconds);
    if (Math.abs(targetScale - scale) < 0.001) scale = targetScale;
    dot.style.transform = `translate(-50%, -50%) scale(${scale})`;
    if (marker.hidden) {
      marker.hidden = false;
      document.body.dataset.pointerVisible = 'true';
    }
    if (scale !== targetScale) frame = requestAnimationFrame(render);
    else previousScaleTime = 0;
  };
  const wake = () => {
    if (visible && !frame) {
      if (!previousScaleTime) previousScaleTime = performance.now();
      frame = requestAnimationFrame(render);
    }
  };
  const resize = () => {
    targetScale = pressed ? 0.7 : hovered ? 1.5 : 1;
    wake();
  };
  const hide = () => {
    hintAvailability(false);
    cancelAnimationFrame(frame);
    cancelAnimationFrame(refreshFrame);
    frame = refreshFrame = 0;
    hasPoint = pressed = hovered = captured = fineInput = false;
    lastElement = null;
    scale = targetScale = 1;
    previousScaleTime = 0;
    marker.hidden = true;
    if (!visible) return;
    delete document.body.dataset.pointerVisible;
    visible = false;
  };
  const refresh = (hit?: Element | null) => {
    if (!hasPoint) return;
    const element = hit ?? document.elementFromPoint(x, y);
    const parent = element?.closest('dialog[open]') ?? document.body;
    if (marker.parentElement !== parent) parent.append(marker);
    if (visible && element === lastElement) {
      hintAvailability(!pressed && !hovered && canHold(element!));
      resize();
      return;
    }
    const field = element?.closest(
      'input,textarea,iframe,[contenteditable]:not([contenteditable="false"])',
    );
    if (
      !element ||
      (!fine.matches && !fineInput) ||
      reduced.matches ||
      forced.matches ||
      (field && !(field instanceof HTMLInputElement && actionInputs.has(field.type)))
    ) {
      hide();
      return;
    }
    visible = true;
    const control = element.closest(clickable);
    const labelControl = control instanceof HTMLLabelElement ? control.control : undefined;
    hovered =
      !!control &&
      !control.closest('[inert],[aria-disabled="true"],:disabled') &&
      (!(control instanceof HTMLLabelElement) ||
        (!!labelControl && !labelControl.matches(':disabled')));
    hintAvailability(!pressed && !hovered && canHold(element));
    lastElement = element;
    resize();
  };
  const move = (event: PointerEvent) => {
    if (event.pointerType === 'touch' || reduced.matches || forced.matches) {
      hide();
      return;
    }
    // Actual mouse/pen input also covers hybrid browsers whose capability
    // queries keep reporting the touchscreen after a mouse is connected.
    fineInput = event.pointerType === 'mouse' || event.pointerType === 'pen';
    if (!fine.matches && !fineInput) {
      hide();
      return;
    }
    if (event.type === 'pointermove' || event.type === 'pointerdown') hintAvailability(false);
    x = event.clientX;
    y = event.clientY;
    hasPoint = true;
    pressed = (event.buttons & 1) !== 0;
    refresh(!captured && event.target instanceof Element ? event.target : undefined);
  };
  const scheduleRefresh = () => {
    if (!hasPoint || refreshFrame) return;
    refreshFrame = requestAnimationFrame(() => {
      refreshFrame = 0;
      lastElement = null;
      refresh();
    });
  };
  for (const type of [
    'pointermove',
    'pointerover',
    'pointerout',
    'pointerdown',
    'pointerup',
  ] as const)
    window.addEventListener(type, move, pointerOptions);
  window.addEventListener(
    'gotpointercapture',
    () => {
      captured = true;
    },
    pointerOptions,
  );
  window.addEventListener(
    'lostpointercapture',
    () => {
      captured = false;
      lastElement = null;
      refresh();
    },
    pointerOptions,
  );
  window.addEventListener('scroll', scheduleRefresh, pointerOptions);
  window.addEventListener('resize', scheduleRefresh, options);
  window.addEventListener('pointercancel', hide, options);
  document.documentElement.addEventListener('pointerleave', hide, options);
  window.addEventListener('blur', hide, options);
  window.addEventListener('pagehide', hide, options);
  document.addEventListener('close', hide, { ...options, capture: true });
  window.addEventListener('keydown', hide, options);
  document.addEventListener(
    'visibilitychange',
    () => {
      if (document.hidden) hide();
    },
    options,
  );
  for (const query of [fine, reduced, forced]) query.addEventListener('change', hide, options);
  return {
    refresh: scheduleRefresh,
    dispose() {
      abort.abort();
      hide();
      marker.remove();
    },
  };
}
