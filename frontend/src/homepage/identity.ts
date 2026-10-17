import { PhysicsCore, PHYSICS } from '../../experiments/segmented-identity/core';
import { canonicalGeometry } from '../../experiments/segmented-identity/geometry';
import { Renderer } from '../../experiments/segmented-identity/renderer';
import { InputAdapter } from '../../experiments/segmented-identity/input';
import { FixedScheduler } from '../../experiments/segmented-identity/scheduler';
import { identityStage } from './identity-stage';
export function physicalIdentity(svg: SVGSVGElement) {
    let core = new PhysicsCore(canonicalGeometry), raf = 0, active = false;
    const origin = { x: 0, y: 0 };
    const renderer = new Renderer(svg, PHYSICS.navigatorRadius), scheduler = new FixedScheduler();
    const homes = new Map(canonicalGeometry.map(g => [g.id, { x:g.home.x+480, y:g.home.y+310 }]));
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const input = new InputAdapter(svg, () => active && !reduced.matches && document.documentElement.dataset.menuOpen !== 'true', wake, { origin: () => origin, touch: true });
    const reset = () => {
        input.deactivate();
        input.take();
        const matrix = svg.getScreenCTM();
        if (!matrix) return;
        const inverse = matrix.inverse();
        const start = new DOMPoint(0, 0).matrixTransform(inverse);
        const end = new DOMPoint(innerWidth, innerHeight).matrixTransform(inverse);
        const stage = identityStage(canonicalGeometry, { x: start.x, y: start.y, width: end.x-start.x, height: end.y-start.y });
        origin.x = start.x;
        origin.y = start.y;
        core = new PhysicsCore(stage.geometry, stage);
        scheduler.reset();
        render();
    };
    const resize = new ResizeObserver(() => { if (active) reset(); });
    resize.observe(svg);
    window.addEventListener('resize', reset);
    const preferences = () => {
        if (reduced.matches) {
            cancelAnimationFrame(raf);
            raf = 0;
            reset();
        } else wake();
    };
    reduced.addEventListener('change', preferences);
    function render() {
        const poses = core.bodies.map(body => {
            const base = homes.get(body.geometry.id)!, home = core.home(body);
            return { geometry:body.geometry, position:{ x:base.x+(body.position.x-home.x), y:base.y+(body.position.y-home.y) }, angle:body.angle };
        });
        renderer.render(poses, { active:core.navigator.active, position:{ x:core.navigator.position.x+origin.x, y:core.navigator.position.y+origin.y } });
        for (const body of core.bodies)
        svg.querySelector(`[data-fragment="${body.geometry.id}"]`)?.classList.toggle('in-contact', body.navigatorContact); }
    function wake() { if (active && !reduced.matches && !raf && (input.pending || !core.isRest()))
        raf = requestAnimationFrame(tick); }
    function tick(now: number) { raf = 0; if (!active || reduced.matches)
        return; scheduler.advance(now, budget => { const before = core.forecastWork.simulatedSteps; const samples = input.take(); if (samples)
        core.stepStroke(samples, budget);
    else
        core.stepFixed(1, undefined, budget); return core.forecastWork.simulatedSteps - before; }, undefined, input.pending); render(); if (!core.isRest() || input.pending)
        wake();
    else
        scheduler.reset(); }
    render();
    return {
        active(value: boolean) { if (value === active)
            return; active = value; if (!value) {
            cancelAnimationFrame(raf);
            raf = 0;
            reset();
        }
        else { reset(); wake(); } },
        dispose() { active = false; cancelAnimationFrame(raf); resize.disconnect(); window.removeEventListener('resize', reset); reduced.removeEventListener('change', preferences); input.dispose(); renderer.dispose(); }
    };
}
