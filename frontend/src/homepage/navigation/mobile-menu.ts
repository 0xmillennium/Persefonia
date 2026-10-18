import { letterReels } from './lettering';

/** Native modal owns focus/inertness; CSS columns share the site's reveal language. */
export function mobileNavigation() {
  const header = document.querySelector<HTMLElement>('.home-nav')!;
  const toggle = header.querySelector<HTMLButtonElement>('[data-menu-toggle]')!;
  const dialog = document.querySelector<HTMLDialogElement>('dialog[data-mobile-menu]')!;
  const close = dialog.querySelector<HTMLButtonElement>('[data-menu-close]')!;
  const mobile = matchMedia('(max-width: 960px)');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const abort = new AbortController(),
    options = { signal: abort.signal };
  const lettering: ReturnType<typeof letterReels>[] = [];
  let frame = 0,
    timer = 0,
    closing = false;
  const enhanceLabels = () => {
    if (lettering.length || document.fonts.status !== 'loaded') return;
    // A closed dialog has no rendered text geometry to measure.
    for (const label of dialog.querySelectorAll<HTMLElement>('[data-nav-label]'))
      lettering.push(letterReels(label, true));
  };
  void document.fonts.ready.then(() => {
    if (!abort.signal.aborted && dialog.open) enhanceLabels();
  });
  const finish = () => {
    cancelAnimationFrame(frame);
    clearTimeout(timer);
    frame = timer = 0;
    closing = false;
    delete dialog.dataset.open;
    delete document.documentElement.dataset.menuOpen;
    toggle.setAttribute('aria-expanded', 'false');
    if (dialog.open) dialog.close();
  };
  const open = () => {
    if (!mobile.matches || (dialog.open && !closing)) return;
    cancelAnimationFrame(frame);
    clearTimeout(timer);
    closing = false;
    if (!dialog.open) dialog.showModal();
    enhanceLabels();
    dialog.getBoundingClientRect();
    document.documentElement.dataset.menuOpen = 'true';
    toggle.setAttribute('aria-expanded', 'true');
    if (reduced.matches) dialog.dataset.open = 'true';
    else
      frame = requestAnimationFrame(() => {
        frame = 0;
        dialog.dataset.open = 'true';
      });
  };
  const dismiss = () => {
    if (!dialog.open) return;
    if (reduced.matches || closing) {
      finish();
      return;
    }
    closing = true;
    cancelAnimationFrame(frame);
    frame = 0;
    delete dialog.dataset.open;
    timer = window.setTimeout(finish, 550);
  };
  toggle.addEventListener('click', open, options);
  close.addEventListener('click', dismiss, options);
  dialog.addEventListener(
    'cancel',
    (event) => {
      event.preventDefault();
      dismiss();
    },
    options,
  );
  dialog.addEventListener(
    'click',
    (event) => {
      if ((event.target as Element).closest('a[href]')) finish();
    },
    options,
  );
  dialog.addEventListener(
    'transitionend',
    (event) => {
      if (
        closing &&
        event.propertyName === 'transform' &&
        event.target === dialog.querySelector('.home-menu__bands i')
      )
        finish();
    },
    options,
  );
  dialog.addEventListener(
    'close',
    () => {
      if (!dialog.open) finish();
    },
    options,
  );
  mobile.addEventListener(
    'change',
    () => {
      if (!mobile.matches) {
        const wasOpen = dialog.open;
        finish();
        if (wasOpen)
          header.querySelector<HTMLAnchorElement>('nav a')?.focus({ preventScroll: true });
      }
    },
    options,
  );
  reduced.addEventListener(
    'change',
    () => {
      if (closing) finish();
    },
    options,
  );
  window.addEventListener('pagehide', finish, options);
  header.dataset.mobileMenu = 'true';
  toggle.hidden = false;
  return () => {
    finish();
    abort.abort();
    for (const letters of lettering) letters.dispose();
    toggle.hidden = true;
    delete header.dataset.mobileMenu;
  };
}
