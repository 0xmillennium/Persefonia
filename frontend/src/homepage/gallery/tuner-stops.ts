export const collectionStop = (index: number, count: number) => ((index % count) + count) % count;
/** Keep the visual turn continuous when a menu or navigation link chooses a wrapped stop. */
export function nearestCollectionStop(current: number, index: number, count: number) {
  const forward = collectionStop(index - current, count);
  return current + (forward > count / 2 ? forward - count : forward);
}
