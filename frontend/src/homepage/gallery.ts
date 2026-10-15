import { galleryIndex, galleryWheelProgress, clamp } from './scenes';
import { galleryDepth, galleryProgress, galleryRelease, gallerySettle } from './gallery-motion';

export function mountGallery(root: HTMLElement, position: (progress: number) => void) {
    const groups = [...root.querySelectorAll<HTMLElement>('.gallery-category')].map(node => ({
        node, group: node.querySelector<HTMLElement>('.gallery-group'), cards: [...node.querySelectorAll<HTMLElement>('.gallery-card')]
    }));
    const radios = [...root.querySelectorAll<HTMLInputElement>('[name="gallery-category"]')];
    const output = root.querySelector<HTMLOutputElement>('[data-gallery-position]')!;
    const space = root.querySelector<HTMLElement>('.gallery-space')!;
    const abort = new AbortController(), options = { signal: abort.signal };
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    let category = history.state?.homepageV2?.category === 'writings' ? 'writings' : 'projects', progress = 0;
    let selectedIndex = 0, targetIndex = 0, frame = 0, previous = 0, active = false;
    let tiltX = 0, tiltY = 0, targetX = 0, targetY = 0;
    let press: { id: number; x: number; y: number; index: number; last: number; time: number; velocity: number; spacing: number; dragging: boolean } | null = null;
    let suppressClick = false;
    const current = () => groups.find(group => group.node.dataset.category === category)!;
    const stop = () => { cancelAnimationFrame(frame); frame = previous = 0; };
    for (const image of root.querySelectorAll<HTMLImageElement>('.gallery-cover img')) {
        image.addEventListener('error', () => { image.hidden = true; }, options);
        if (image.complete && image.naturalWidth === 0) image.hidden = true;
    }
    function render() {
        const group = current();
        group.group?.style.setProperty('transform', reduced.matches ? '' : `rotateY(${tiltX}deg) rotateX(${tiltY}deg)`);
        const radius = innerWidth < 700 ? innerWidth * .9 : Math.min(1000, innerWidth * .72);
        group.cards.forEach((card, i) => {
            const distance = Math.abs(i - selectedIndex), theta = (i - selectedIndex) * (innerWidth < 700 ? .78 : .48);
            card.style.transform = reduced.matches ? '' : `translateX(${Math.sin(theta) * radius}px) translateZ(${(Math.cos(theta) - 1) * radius}px) rotateY(${-theta * 180 / Math.PI}deg)`;
            const depth = galleryDepth(distance);
            if (reduced.matches) {
                card.style.removeProperty('--gallery-blur');
                card.style.removeProperty('--gallery-depth-opacity');
            } else {
                card.style.setProperty('--gallery-blur', `${depth.blur}px`);
                card.style.setProperty('--gallery-depth-opacity', String(depth.opacity));
            }
            card.dataset.front = String(distance < .5);
            card.style.visibility = distance > 3 && !reduced.matches ? 'hidden' : 'inherit';
        });
        const text = group.cards.length ? `${Math.round(targetIndex) + 1} / ${group.cards.length}` : '0 / 0';
        if (output.value !== text) output.value = text;
    }
    function tick(now: number) {
        frame = 0;
        if (!active || reduced.matches || document.hidden) { previous = 0; return; }
        const seconds = previous ? Math.min(.05, (now - previous) / 1000) : 1 / 60;
        previous = now;
        selectedIndex = press?.dragging ? targetIndex : gallerySettle(selectedIndex, targetIndex, seconds);
        tiltX = gallerySettle(tiltX, targetX, seconds);
        tiltY = gallerySettle(tiltY, targetY, seconds);
        render();
        if (selectedIndex !== targetIndex || tiltX !== targetX || tiltY !== targetY) wake();
        else previous = 0;
    }
    function wake() { if (active && !reduced.matches && !document.hidden && !frame) frame = requestAnimationFrame(tick); }
    function update(p: number) {
        progress = p;
        targetIndex = galleryIndex(progress, current().cards.length);
        if (!active || reduced.matches || press?.dragging) { selectedIndex = targetIndex; render(); }
        else wake();
    }
    function finish(settle: boolean, momentum = true) {
        if (!press) return;
        const gesture = press;
        press = null;
        delete root.dataset.dragging;
        if (space.hasPointerCapture(gesture.id)) space.releasePointerCapture(gesture.id);
        if (!gesture.dragging) return;
        if (settle) {
            const velocity = !momentum || performance.now() - gesture.time > 80 ? 0 : gesture.velocity;
            const count = current().cards.length;
            const index = galleryRelease(selectedIndex, velocity, count);
            update(count < 2 ? 0 : index / (count - 1));
            position(progress);
        } else { targetIndex = selectedIndex; stop(); }
    }
    function select(value: string) {
        if (value === category) return;
        finish(false);
        category = value;
        root.dataset.category = category;
        root.querySelectorAll<HTMLElement>('[data-gallery-more]').forEach(link => link.hidden = link.dataset.galleryMore !== category);
        for (const radio of radios) radio.checked = radio.value === category;
        for (const group of groups) {
            group.node.hidden = group.node.dataset.category !== category;
            group.node.inert = group.node.hidden;
        }
        update(progress);
    }
    radios.forEach(radio => radio.addEventListener('change', () => select(radio.value), options));
    const step = (amount: number) => {
        const from = targetIndex;
        finish(false);
        const count = current().cards.length;
        const p = count < 2 ? 0 : clamp((Math.round(from) + amount) / (count - 1));
        update(p);
        position(p);
    };
    root.querySelector('[data-gallery-prev]')?.addEventListener('click', () => step(-1), options);
    root.querySelector('[data-gallery-next]')?.addEventListener('click', () => step(1), options);
    root.addEventListener('focusin', event => {
        if (press) return;
        const card = (event.target as Element).closest<HTMLElement>('.gallery-card');
        if (!card) return;
        const cards = current().cards, index = cards.indexOf(card);
        if (index >= 0 && Math.abs(index - targetIndex) > .5) {
            finish(false);
            const p = cards.length < 2 ? 0 : index / (cards.length - 1);
            update(p);
            position(p);
        }
    }, options);
    space.addEventListener('dragstart', event => { event.preventDefault(); }, options);
    root.addEventListener('pointerdown', () => { suppressClick = false; }, { ...options, capture: true });
    space.addEventListener('pointerdown', e => {
        if (!active || reduced.matches || !e.isPrimary || e.button !== 0 || current().cards.length < 2 || press
            || (e.target as Element).closest('button,input,select,textarea,[contenteditable="true"]')) return;
        const radius = innerWidth < 700 ? innerWidth * .9 : Math.min(1000, innerWidth * .72);
        press = { id: e.pointerId, x: e.clientX, y: e.clientY, index: selectedIndex, last: selectedIndex,
            time: performance.now(), velocity: 0, spacing: radius * Math.sin(innerWidth < 700 ? .78 : .48), dragging: false };
    }, options);
    space.addEventListener('pointermove', e => {
        if (!press || e.pointerId !== press.id) return;
        const dx = e.clientX - press.x, dy = e.clientY - press.y;
        if (!press.dragging) {
            if (Math.abs(dy) > 8 && Math.abs(dy) >= Math.abs(dx)) { finish(false); return; }
            if (Math.abs(dx) < 8) return;
            press.dragging = true;
            suppressClick = true;
            root.dataset.dragging = 'true';
            space.setPointerCapture(e.pointerId);
        }
        e.preventDefault();
        const now = performance.now(), count = current().cards.length;
        const index = Math.max(0, Math.min(count - 1, press.index - dx / press.spacing));
        press.velocity = .6 * press.velocity + .4 * (index - press.last) / Math.max(.008, (now - press.time) / 1000);
        press.last = index;
        press.time = now;
        update(galleryProgress(index, count));
        position(progress);
    }, options);
    window.addEventListener('pointerup', e => { if (e.pointerId === press?.id) finish(true); }, options);
    window.addEventListener('pointercancel', e => { if (e.pointerId === press?.id) finish(true, false); }, options);
    space.addEventListener('lostpointercapture', e => { if (e.target === space && e.pointerId === press?.id) finish(true, false); }, options);
    root.addEventListener('click', e => {
        if (suppressClick && e instanceof MouseEvent && e.detail > 0) { e.preventDefault(); e.stopPropagation(); }
        suppressClick = false;
    }, { ...options, capture: true });
    window.addEventListener('blur', () => { finish(false); stop(); }, options);
    window.addEventListener('resize', () => { finish(false); render(); }, options);
    window.addEventListener('pagehide', () => { finish(false); stop(); }, options);
    document.addEventListener('visibilitychange', () => { if (document.hidden) { finish(false); stop(); } else wake(); }, options);
    reduced.addEventListener('change', () => { finish(false); stop(); selectedIndex = targetIndex; tiltX = tiltY = targetX = targetY = 0; render(); }, options);
    const initial = category;
    category = '';
    select(initial);
    return {
        update,
        active(value: boolean) {
            if (active === value) return;
            active = value;
            if (!active) { finish(false); stop(); selectedIndex = targetIndex; render(); }
            else wake();
        },
        orient(x: number, y: number) { targetX = x * 3; targetY = -y * 1.5; wake(); },
        select,
        wheel(currentProgress: number, direction: number) {
            finish(false);
            const target = galleryWheelProgress(currentProgress, current().cards.length, direction);
            if (target === null) return false;
            update(target);
            position(target);
            return true;
        },
        get category() { return category; },
        dispose() {
            finish(false); stop(); abort.abort();
            for (const group of groups) {
                group.node.hidden = false; group.node.inert = false;
                group.group?.style.removeProperty('transform');
                for (const card of group.cards) {
                    for (const property of ['transform', 'visibility', '--gallery-blur', '--gallery-depth-opacity']) card.style.removeProperty(property);
                    delete card.dataset.front;
                }
            }
            root.querySelectorAll<HTMLElement>('[data-gallery-more]').forEach(link => link.hidden = false);
        }
    };
}
