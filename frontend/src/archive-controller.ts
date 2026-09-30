import {
  chapterPosition, desktopArchiveStates, interpolateNumber, mobileArchiveStates, tabletArchiveStates,
  type ArchivePose, type Breakpoint, type GeometryPose, type SheetPose
} from "./archive-model";

function breakpoint(): Breakpoint {
  return window.innerWidth <= 672 ? "mobile" : window.innerWidth <= 1024 ? "tablet" : "desktop";
}

function setMaterial(element: HTMLElement, pose: SheetPose): void {
  element.style.transform = `translate3d(${pose[0]}px, ${pose[1]}px, 0) scale(${pose[2]}, ${pose[3]}) rotate(${pose[4]}deg)`;
  element.style.opacity = String(pose[5]);
}

function setLine(element: SVGLineElement, pose: GeometryPose): void {
  element.setAttribute("x1", String(pose[0]));
  element.setAttribute("y1", String(pose[1]));
  element.setAttribute("x2", String(pose[2]));
  element.setAttribute("y2", String(pose[3]));
  element.style.opacity = String(pose[4]);
}

function mix<T extends readonly number[]>(a: T, b: T, progress: number): T {
  return a.map((value, index) => interpolateNumber(value, b[index] ?? value, progress)) as unknown as T;
}

export function enhanceArchive(): void {
  const stage = document.querySelector<HTMLElement>(".archive-stage");
  if (!stage) return;
  const folio = stage.querySelector<HTMLElement>("[data-archive-folio]");
  const sheets = Array.from(stage.querySelectorAll<HTMLElement>("[data-archive-sheet]"));
  const vellum = stage.querySelector<HTMLElement>("[data-archive-vellum]");
  const chapters = Array.from(document.querySelectorAll<HTMLElement>("[data-archive-chapter]"));
  const spine = stage.querySelector<SVGLineElement>("[data-archive-spine]");
  const seam = stage.querySelector<SVGLineElement>("[data-archive-seam]");
  const branch = stage.querySelector<SVGLineElement>("[data-archive-branch]");
  const trace = stage.querySelector<SVGCircleElement>("[data-archive-trace]");
  const joint = stage.querySelector<SVGCircleElement>("[data-archive-joint]");
  if (!folio || !vellum || sheets.length !== 4 || chapters.length !== 7 ||
      !spine || !seam || !branch || !trace || !joint) return;

  const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  let anchors: number[] = [];
  let mode: Breakpoint = breakpoint();
  let queued = false;
  let measureQueued = false;
  let active = false;

  const render = (): void => {
    queued = false;
    if (!active || document.hidden) return;
    const states = mode === "mobile" ? mobileArchiveStates : mode === "tablet" ? tabletArchiveStates : desktopArchiveStates;
    const position = chapterPosition(window.scrollY, anchors);
    const index = Math.min(6, Math.floor(position));
    const progress = position - index;
    const from: ArchivePose = states[index]!;
    const to: ArchivePose = states[Math.min(6, index + 1)]!;
    const global = mix(from.folio, to.folio, progress);
    folio.style.transform = `translate3d(${global[0]}px, ${global[1]}px, 0) scale(${global[2]})`;
    folio.style.opacity = String(global[3]);
    sheets.forEach((sheet, part) => setMaterial(sheet, mix(from.sheets[part]!, to.sheets[part]!, progress)));
    setMaterial(vellum, mix(from.vellum, to.vellum, progress));
    setLine(spine, mix(from.spine, to.spine, progress));
    setLine(seam, mix(from.seam, to.seam, progress));
    setLine(branch, mix(from.branch, to.branch, progress));
    const tracePose = mix(from.trace, to.trace, progress);
    trace.setAttribute("cx", String(tracePose[0]));
    trace.setAttribute("cy", String(tracePose[1]));
    trace.style.opacity = String(tracePose[2]);
    const jointPose = mix(from.joint, to.joint, progress);
    joint.setAttribute("cx", String(jointPose[0]));
    joint.setAttribute("cy", String(jointPose[1]));
    joint.style.opacity = String(jointPose[2]);
    stage.dataset.archivePosition = String(position);
  };
  const queue = (): void => {
    if (active && !queued && !document.hidden) {
      queued = true;
      requestAnimationFrame(render);
    }
  };
  const measure = (): void => {
    measureQueued = false;
    mode = breakpoint();
    anchors = chapters.map(chapter =>
      chapter.getBoundingClientRect().top + window.scrollY + chapter.offsetHeight / 2 - window.innerHeight / 2
    );
    queue();
  };
  const queueMeasure = (): void => {
    if (!measureQueued && !document.hidden) {
      measureQueued = true;
      requestAnimationFrame(measure);
    }
  };
  const updateMotion = (): void => {
    active = !motion.matches;
    document.documentElement.classList.toggle("archive-enhanced", active);
    if (active) queueMeasure();
  };

  // Validate required geometry and the motion query before installing listeners or hiding SSR snapshots.
  measure();
  const observer = new ResizeObserver(queueMeasure);
  chapters.forEach(chapter => observer.observe(chapter));
  window.addEventListener("scroll", queue, { passive: true });
  window.addEventListener("resize", queueMeasure, { passive: true });
  document.addEventListener("visibilitychange", () => { if (!document.hidden) queueMeasure(); });
  motion.addEventListener("change", updateMotion);
  document.fonts.ready.then(queueMeasure).catch(() => {});
  document.querySelectorAll<HTMLAnchorElement>(".home-nav__mobile nav a").forEach(link => {
    link.addEventListener("click", () => link.closest("details")?.removeAttribute("open"));
  });
  updateMotion();
}
