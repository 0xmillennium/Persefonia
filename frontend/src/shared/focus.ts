/** Keep focus on an available navigation control when a scene becomes inert. */
export function focusNavigation(doc: Document = document): void {
  const toggle = doc.querySelector<HTMLElement>('[data-menu-toggle]');
  const target = toggle?.getClientRects().length
    ? toggle
    : doc.querySelector<HTMLElement>('.home-nav nav a');
  target?.focus({ preventScroll: true });
}
