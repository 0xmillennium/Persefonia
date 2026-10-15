import { IdentityAnimation, dwell } from '../../experiments/identity-effects/src/animation';
import { variants, timing } from '../../experiments/identity-effects/src/choreography';
/** Trigger adaptation only: authored Experiment 04 choreography and lettering are shared. */
export function compactIdentity(root: HTMLElement, loading = false) {
    const button = root.querySelector<HTMLButtonElement>('button')!;
    const animation = new IdentityAnimation(root.querySelector<SVGSVGElement>('svg')!);
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const lifetime = new AbortController(), options = { signal: lifetime.signal };
    let task = new AbortController(), cycle = 0, engaged = false, suspended = false;
    async function run(signal: AbortSignal) {
        await animation.reset(reduced.matches, signal);
        do {
            const variant = cycle++ % variants.length;
            root.dataset.variant = variants[variant]!.id;
            await animation.open(variant, reduced.matches, signal);
            if (signal.aborted)
                return;
            await animation.close(variant, reduced.matches, signal);
            if (signal.aborted)
                return;
            await dwell(reduced.matches ? 120 : timing.closed, signal);
            if (signal.aborted)
                return;
            await animation.reveal(reduced.matches, signal);
            if (signal.aborted)
                return;
            if (!loading)
                return;
            await dwell(reduced.matches ? timing.quietHold : timing.hold, signal);
            if (signal.aborted)
                return;
            await animation.reset(reduced.matches, signal);
        } while (!signal.aborted);
    }
    const reconcile = () => { task.abort(); task = new AbortController(); if (document.hidden || suspended) {
        animation.clear();
        return;
    } if (loading || engaged)
        void run(task.signal).catch(() => animation.clear());
    else
        void animation.reset(reduced.matches, task.signal); };
    button.addEventListener('pointerenter', e => { if (e.pointerType === 'touch')
        return; engaged = true; reconcile(); }, options);
    button.addEventListener('pointerleave', () => { engaged = false; reconcile(); }, options);
    button.addEventListener('focus', () => { engaged = true; reconcile(); }, options);
    button.addEventListener('blur', () => { engaged = false; reconcile(); }, options);
    // Focus commonly precedes touch/keyboard activation; activation must not
    // toggle off the choreography that focus just started.
    button.addEventListener('click', () => { engaged = true; reconcile(); }, options);
    document.addEventListener('pointerdown', event => {
        if (event.pointerType === 'touch' && engaged && !button.contains(event.target as Node)) {
            engaged = false; reconcile();
        }
    }, options);
    button.addEventListener('keydown', e => { if (e.key === 'Escape') {
        engaged = false;
        reconcile();
    } }, options);
    document.addEventListener('visibilitychange', reconcile, options);
    window.addEventListener('pagehide', () => { suspended = true; engaged = false; reconcile(); }, options);
    window.addEventListener('pageshow', () => { suspended = false; reconcile(); }, options);
    reduced.addEventListener('change', () => { animation.clear(); reconcile(); }, options);
    reconcile();
    return { dispose() { task.abort(); lifetime.abort(); animation.destroy(); } };
}
