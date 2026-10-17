import { gallerySettle } from './gallery-motion';

export const collectionStop = (index: number, count: number) => ((index % count) + count) % count;
/** Keep the visual turn continuous when a menu or navigation link chooses a wrapped stop. */
export function nearestCollectionStop(current: number, index: number, count: number) {
    const forward = collectionStop(index - current, count);
    return current + (forward > count / 2 ? forward - count : forward);
}

/** A looping cylinder. Wheel ownership belongs to the beginning of each gesture. */
export function collectionTuner(directory: HTMLElement, select: (value: string, direction: number) => void) {
    const button = directory.querySelector<HTMLButtonElement>('.collection-tuner')!;
    const list = directory.querySelector<HTMLElement>('.collection-options')!;
    const radios = [...list.querySelectorAll<HTMLInputElement>('input')];
    const name = directory.querySelector<HTMLElement>('.tuner-name')!;
    const names = directory.querySelector<HTMLElement>('.tuner-names')!;
    const scale = directory.querySelector<HTMLElement>('.tuner-scale')!;
    const labels = radios.map(radio => radio.nextElementSibling!.textContent!.trim());
    const label = button.getAttribute('aria-label')!;
    const abort = new AbortController(), options = { signal: abort.signal };
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    let committed = 0, displayed = 0, view = 0, target = 0, pending: number | null = null;
    let frame = 0, previous = 0;
    let wheelTime = -Infinity, wheelOwner = false, wheelSum = 0, wheelDirection = 0, lastTurn = -Infinity;
    let drag: { id: number; x: number; y: number; index: number; moved: boolean } | null = null;
    let suppressClick = false;
    const ticks = Array.from({ length: 57 }, () => {
        const tick = document.createElement('i'); scale.append(tick); return tick;
    });
    const reels = Array.from({ length: 7 }, () => {
        const node = document.createElement('span'); node.className = 'tuner-label'; names.append(node); return node;
    });
    const wrapped = (index: number) => collectionStop(index, radios.length);
    function render(position: number) {
        view = position;
        displayed = Math.round(position);
        name.textContent = labels[wrapped(displayed)]!;
        const radius = button.clientWidth * .48;
        const centerTick = Math.floor(position * 8);
        for (let index = centerTick - 28; index <= centerTick + 28; index++) {
            const tick = ticks[collectionStop(index, ticks.length)]!;
            const distance = (index - position * 8) / 18;
            const tickClass = index % 8 === 0 ? 'tuner-major' : '';
            if (tick.className !== tickClass) tick.className = tickClass;
            tick.style.transform = `translateX(${Math.sin(Math.max(-1, Math.min(1, distance)) * Math.PI / 2) * radius}px)`;
            tick.style.opacity = Math.abs(distance) < 1 ? String(Math.pow(Math.cos(distance * Math.PI / 2), 1.5) * .7) : '0';
        }
        const center = Math.round(position);
        for (let index = center - 3; index <= center + 3; index++) {
            const node = reels[collectionStop(index, reels.length)]!;
            const offset = index - position, distance = Math.abs(offset);
            const angle = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, offset * .78));
            const text = labels[wrapped(index)]!;
            if (node.textContent !== text) node.textContent = text;
            node.dataset.current = String(index === center);
            node.dataset.offset = String(index - center);
            node.style.transform = `translate3d(${Math.sin(angle) * radius}px,${(1 - Math.cos(angle)) * 5}px,${(Math.cos(angle) - 1) * radius}px) rotateY(${angle * 180 / Math.PI}deg)`;
            node.style.opacity = String(Math.max(0, 1 - Math.pow(distance / 1.8, 1.2)));
            node.style.visibility = distance < 1.8 ? 'inherit' : 'hidden';
        }
        directory.dataset.tunerPosition = String(position);
    }
    function stop() { cancelAnimationFrame(frame); frame = previous = 0; }
    function tick(now: number) {
        frame = 0;
        const seconds = previous ? Math.min(.05, (now - previous) / 1000) : 1 / 60;
        previous = now;
        render(gallerySettle(view, target, seconds));
        if (view !== target) frame = requestAnimationFrame(tick); else previous = 0;
    }
    function settle(index: number, animate = true) {
        target = index;
        if (!animate || reduced.matches || document.hidden) { stop(); render(index); }
        else if (!frame && view !== target) frame = requestAnimationFrame(tick);
    }
    function open(value: boolean, focus = false) {
        list.hidden = !value;
        button.setAttribute('aria-expanded', String(value));
        if (value && focus) radios[wrapped(committed)]!.focus({ preventScroll: true });
    }
    function choose(index: number) {
        const next = wrapped(index);
        open(false);
        if (next !== wrapped(committed)) {
            pending = index;
            select(radios[next]!.value, Math.sign(index - committed));
            pending = null;
        } else { committed = index; settle(index); }
    }
    function finish(cancel = false) {
        if (!drag) return;
        const gesture = drag;
        drag = null;
        delete directory.dataset.tuning;
        if (button.hasPointerCapture(gesture.id)) button.releasePointerCapture(gesture.id);
        if (cancel || !gesture.moved) settle(committed); else choose(displayed);
    }
    button.addEventListener('click', event => {
        if (suppressClick && event.detail > 0) { suppressClick = false; event.preventDefault(); return; }
        suppressClick = false;
        open(list.hidden === true, event.detail === 0);
    }, options);
    directory.addEventListener('keydown', event => {
        const direction = ['ArrowRight', 'ArrowDown'].includes(event.key) ? 1 : ['ArrowLeft', 'ArrowUp'].includes(event.key) ? -1 : 0;
        if (direction || event.key === 'Home' || event.key === 'End') {
            event.preventDefault(); event.stopPropagation();
            finish(true);
            choose(direction ? committed + direction : nearestCollectionStop(committed, event.key === 'Home' ? 0 : radios.length - 1, radios.length));
            button.focus({ preventScroll: true });
        }
        if (event.key === 'Escape') { event.preventDefault(); finish(true); open(false); button.focus({ preventScroll: true }); }
    }, options);
    list.addEventListener('change', () => { open(false); button.focus({ preventScroll: true }); }, options);
    list.addEventListener('click', event => {
        if (event.target instanceof HTMLInputElement && event.target.value === radios[wrapped(committed)]!.value) {
            open(false); button.focus({ preventScroll: true });
        }
    }, options);
    directory.addEventListener('focusout', event => { if (!directory.contains(event.relatedTarget as Node | null)) open(false); }, options);
    document.addEventListener('pointerdown', event => { if (!directory.contains(event.target as Node)) open(false); }, options);
    button.addEventListener('pointerdown', event => {
        if (!event.isPrimary || event.button !== 0) return;
        suppressClick = false;
        stop();
        drag = { id: event.pointerId, x: event.clientX, y: event.clientY, index: view, moved: false };
    }, options);
    button.addEventListener('pointermove', event => {
        if (!drag || drag.id !== event.pointerId) return;
        const dx = event.clientX - drag.x, dy = event.clientY - drag.y;
        if (!drag.moved) {
            if (Math.abs(dy) > 7 && Math.abs(dy) >= Math.abs(dx)) { finish(true); return; }
            if (Math.abs(dx) < 7) return;
            drag.moved = suppressClick = true;
            directory.dataset.tuning = 'true';
            open(false);
            button.setPointerCapture(event.pointerId);
        }
        event.preventDefault();
        render(drag.index - dx / (button.clientWidth / 3));
    }, options);
    window.addEventListener('pointerup', event => { if (event.pointerId === drag?.id) finish(); }, options);
    window.addEventListener('pointercancel', event => { if (event.pointerId === drag?.id) finish(true); }, options);
    button.addEventListener('lostpointercapture', event => { if (event.pointerId === drag?.id) finish(true); }, options);
    window.addEventListener('blur', () => { finish(true); settle(committed, false); open(false); wheelTime = -Infinity; }, options);
    window.addEventListener('resize', () => { finish(true); settle(committed, false); }, options);
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) { finish(true); settle(committed, false); open(false); wheelTime = -Infinity; }
    }, options);
    reduced.addEventListener('change', () => { finish(true); settle(committed, false); }, options);
    const observer = new ResizeObserver(() => render(view));
    observer.observe(button);
    return {
        update(value: string, animate = true) {
            const index = radios.findIndex(radio => radio.value === value);
            if (index < 0) return;
            committed = pending !== null && wrapped(pending) === index ? pending : nearestCollectionStop(committed, index, radios.length);
            button.setAttribute('aria-label', `${label}: ${labels[index]}`);
            settle(committed, animate);
        },
        select(value: string) {
            const index = radios.findIndex(radio => radio.value === value);
            if (index >= 0) choose(nearestCollectionStop(committed, index, radios.length));
        },
        wheel(event: WheelEvent) {
            if (event.ctrlKey || event.metaKey || !event.cancelable || event.defaultPrevented) return false;
            const now = performance.now();
            if (now - wheelTime > 180) {
                wheelOwner = directory.contains(event.target as Node) && !!directory.closest('[data-visible="true"]') && !directory.closest('[inert]');
                wheelSum = wheelDirection = 0;
            }
            wheelTime = now;
            if (!wheelOwner) return false;
            event.preventDefault();
            finish(true);
            const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY;
            const direction = Math.sign(delta);
            if (!direction) return true;
            if (direction !== wheelDirection) wheelSum = 0;
            wheelDirection = direction;
            const normalized = delta * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? innerHeight : 1);
            if (Math.abs(normalized) >= 40) { choose(committed + direction); wheelSum = 0; lastTurn = now; }
            else {
                wheelSum += normalized;
                if (Math.abs(wheelSum) >= 60 && now - lastTurn >= 160) {
                    choose(committed + direction); wheelSum = 0; lastTurn = now;
                }
            }
            return true;
        },
        dispose() { finish(true); stop(); abort.abort(); observer.disconnect(); open(false); scale.replaceChildren(); names.replaceChildren(); delete directory.dataset.tunerPosition; }
    };
}
