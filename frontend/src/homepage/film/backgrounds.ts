import { frameAt } from './model';

export type BackgroundPlayback = 'scroll' | 'loop';
export interface BackgroundDefinition<
  Id extends string = string,
  Playback extends BackgroundPlayback = BackgroundPlayback,
> {
  readonly id: Id;
  readonly playback: Playback;
}

export const BACKGROUNDS = [
  { id: 'waterfall', playback: 'loop' },
  { id: 'robotic-arm', playback: 'scroll' },
  { id: 'tree', playback: 'scroll' },
] as const satisfies readonly BackgroundDefinition[];

export type BackgroundEntry<Registry extends readonly BackgroundDefinition[] = typeof BACKGROUNDS> =
  Registry[number];
export type Background<Registry extends readonly BackgroundDefinition[] = typeof BACKGROUNDS> =
  BackgroundEntry<Registry>['id'];
export type Film<Registry extends readonly BackgroundDefinition[] = typeof BACKGROUNDS> = Extract<
  BackgroundEntry<Registry>,
  { readonly playback: 'scroll' }
>['id'];
export type LoopBackground<Registry extends readonly BackgroundDefinition[] = typeof BACKGROUNDS> =
  Extract<BackgroundEntry<Registry>, { readonly playback: 'loop' }>['id'];
export type FrameKey<Id extends string = Film> = `${Id}:${number}`;
export type BackgroundKey<Id extends string = Background> = `${Id}:${number | 'opening' | 'final'}`;

function resolveRegistry<const Registry extends readonly BackgroundDefinition[]>(
  backgrounds?: Registry,
): readonly BackgroundDefinition[] {
  return Array.isArray(backgrounds) ? backgrounds : BACKGROUNDS;
}

export function isBackground<const Registry extends readonly BackgroundDefinition[]>(
  value: unknown,
  backgrounds: Registry,
): value is Background<Registry>;
export function isBackground(value: unknown): value is Background;
export function isBackground(
  value: unknown,
  backgrounds?: readonly BackgroundDefinition[],
): boolean {
  return (
    typeof value === 'string' && resolveRegistry(backgrounds).some((scene) => scene.id === value)
  );
}

export function backgroundPlayback<
  const Registry extends readonly BackgroundDefinition[] = typeof BACKGROUNDS,
>(value: unknown, backgrounds?: Registry): BackgroundPlayback | undefined {
  if (typeof value !== 'string') return undefined;
  return resolveRegistry(backgrounds).find((scene) => scene.id === value)?.playback;
}

export function isScrollFilm<const Registry extends readonly BackgroundDefinition[]>(
  value: unknown,
  backgrounds: Registry,
): value is Film<Registry>;
export function isScrollFilm(value: unknown): value is Film;
export function isScrollFilm(
  value: unknown,
  backgrounds?: readonly BackgroundDefinition[],
): boolean {
  return backgroundPlayback(value, backgrounds) === 'scroll';
}

export function isLoopBackground<const Registry extends readonly BackgroundDefinition[]>(
  value: unknown,
  backgrounds: Registry,
): value is LoopBackground<Registry>;
export function isLoopBackground(value: unknown): value is LoopBackground;
export function isLoopBackground(
  value: unknown,
  backgrounds?: readonly BackgroundDefinition[],
): boolean {
  return backgroundPlayback(value, backgrounds) === 'loop';
}

export function nextBackground(value: Background): Background;
export function nextBackground<const Registry extends readonly BackgroundDefinition[]>(
  value: Background<Registry>,
  backgrounds: Registry,
): Background<Registry>;
export function nextBackground(
  value: string,
  backgrounds?: readonly BackgroundDefinition[],
): string {
  const registry = resolveRegistry(backgrounds);
  const index = registry.findIndex((scene) => scene.id === value);
  return registry[(index >= 0 ? index + 1 : 0) % registry.length]!.id;
}

/** Selection and page position do not own a looping video's playback clock. */
export class BackgroundTransport<
  const Registry extends readonly BackgroundDefinition[] = typeof BACKGROUNDS,
> {
  film: Background<Registry>;
  desired = 0;
  presented: BackgroundKey<Background<Registry>> | null = null;
  constructor(private readonly backgrounds: Registry = BACKGROUNDS as unknown as Registry) {
    this.film = this.backgrounds[0]!.id;
  }
  setProgress(progress: number) {
    this.desired = frameAt(progress);
  }
  present(key: BackgroundKey<Background<Registry>>) {
    if (
      !key.startsWith(`${this.film}:`) ||
      (isScrollFilm(this.film, this.backgrounds) &&
        !key.endsWith(':opening') &&
        !key.endsWith(':final') &&
        key !== `${this.film}:${this.desired}`)
    )
      return false;
    this.presented = key;
    return true;
  }
  switchFilm() {
    this.film = nextBackground(this.film, this.backgrounds);
  }
  restore(film: Background<Registry>) {
    if (!isBackground(film, this.backgrounds)) return;
    this.film = film;
    this.presented = null;
  }
}
