const KEY = 'persefonia-navigation-cover';
export function clearNavigationMarker() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    /* Native navigation needs no storage. */
  }
}
export function rememberNavigation(url: string) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ url, time: Date.now() }));
    return true;
  } catch {
    return false;
  }
}
/** Consume before any optional animation import; stale markers never block content. */
export function consumeNavigationMarker(
  url: string,
  navigationType: string | undefined,
  now = Date.now(),
) {
  let raw: string | null = null;
  try {
    raw = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
  } catch {
    return false;
  }
  if (!raw || navigationType === 'back_forward') return false;
  try {
    const data = JSON.parse(raw);
    return (
      data.url === url &&
      Number.isFinite(data.time) &&
      now - data.time >= 0 &&
      now - data.time <= 60000
    );
  } catch {
    return false;
  }
}
