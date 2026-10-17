export function wheelPath(
  readingEnd: number,
  galleryStops: number[],
  footer: number,
  bottom: number,
  step: number,
) {
  const positions = [0];
  const append = (position: number) => {
    const y = Math.round(Math.max(0, Math.min(bottom, position)));
    if (y > positions[positions.length - 1]!) positions.push(y);
  };
  for (let y = step; y < readingEnd; y += step) append(y);
  append(readingEnd);
  for (const y of galleryStops) append(y);
  append(footer);
  for (let y = footer + step; y < bottom; y += step) append(y);
  append(bottom);
  return positions;
}

export function wheelDestination(positions: number[], origin: number, direction: number) {
  if (direction > 0)
    return positions.find((y) => y > origin + 0.5) ?? positions[positions.length - 1]!;
  for (let i = positions.length - 1; i >= 0; i--)
    if (positions[i]! < origin - 0.5) return positions[i]!;
  return positions[0]!;
}
