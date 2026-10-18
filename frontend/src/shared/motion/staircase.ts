/** Readiness-driven sibling of the reversible, scroll-derived staircase primitive. */
export async function exitStaircase(
  bands: HTMLElement[],
  reduced: boolean,
  followingSheet = false,
  followingLayer?: HTMLElement,
) {
  if (reduced) return;
  const animations = bands.map((band, i) =>
    band
      .animate([{ transform: 'translateY(0)' }, { transform: 'translateY(-120%)' }], {
        duration: 560,
        delay: followingSheet ? (i < 8 ? 100 + i * 32 : 0) : i * 32,
        easing: 'cubic-bezier(.22,1,.36,1)',
        fill: 'both',
      })
      .finished.catch(() => {}),
  );
  if (followingLayer)
    animations.push(
      followingLayer
        .animate([{ transform: 'translateY(0)' }, { transform: 'translateY(-120%)' }], {
          duration: 560,
          delay: 320,
          easing: 'cubic-bezier(.22,1,.36,1)',
          fill: 'both',
        })
        .finished.catch(() => {}),
    );
  await Promise.all(animations);
}
