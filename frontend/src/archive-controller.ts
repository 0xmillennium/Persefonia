import {
  chapterPosition, desktopArchiveStates, interpolateNumber, interpolateSheetProminence, mobileArchiveStates, tabletArchiveStates,
  type ArchivePose, type Breakpoint, type GeometryPose, type PointPose, type SheetPose
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

function setPoint(element: SVGCircleElement, pose: PointPose): void {
  element.setAttribute("cx", String(pose[0]));
  element.setAttribute("cy", String(pose[1]));
  element.style.opacity = String(pose[2]);
}

function mix<T extends readonly number[]>(a: T, b: T, progress: number): T {
  return a.map((value, index) => interpolateNumber(value, b[index] ?? value, progress)) as unknown as T;
}

export function enhanceArchive(): void {
  const stage = document.querySelector<HTMLElement>(".archive-stage");
  if (!stage) return;
  const folio = stage.querySelector<HTMLElement>("[data-archive-folio]");
  const sheets = Array.from(stage.querySelectorAll<HTMLElement>("[data-archive-sheet]"));
  const vellums = Array.from(stage.querySelectorAll<HTMLElement>("[data-archive-vellum]"));
  const tabs = Array.from(stage.querySelectorAll<HTMLElement>("[data-archive-tab]"));
  const chapters = Array.from(document.querySelectorAll<HTMLElement>("[data-archive-chapter]"));
  const spine = stage.querySelector<SVGLineElement>("[data-archive-spine]");
  const ticks = Array.from(stage.querySelectorAll<SVGLineElement>("[data-archive-tick]"));
  const seams = Array.from(stage.querySelectorAll<SVGLineElement>("[data-archive-seam]"));
  const branch = stage.querySelector<SVGLineElement>("[data-archive-branch]");
  const traces = Array.from(stage.querySelectorAll<SVGCircleElement>("[data-archive-trace]"));
  const joints = Array.from(stage.querySelectorAll<SVGCircleElement>("[data-archive-joint]"));
  if (!folio || !spine || !branch || sheets.length !== 4 || vellums.length !== 2 ||
      tabs.length !== 4 || chapters.length !== 7 || ticks.length !== 12 ||
      seams.length !== 4 || traces.length !== 3 || joints.length !== 4) return;

  const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
  let anchors: number[] = [];
  let mode: Breakpoint = breakpoint();
  let renderFrameId = 0;
  let measureFrameId = 0;
  let active = false;
  let failed = false;
  let observer: ResizeObserver | undefined;

  const stop = (): void => {
    active = false;
    cancelAnimationFrame(renderFrameId);
    cancelAnimationFrame(measureFrameId);
    renderFrameId = 0;
    measureFrameId = 0;
    document.documentElement.classList.remove("archive-enhanced");
  };
  const rollback = (error: unknown): void => {
    failed = true;
    stop();
    observer?.disconnect();
    window.removeEventListener("scroll", queue);
    window.removeEventListener("resize", queueMeasure);
    document.removeEventListener("visibilitychange", visibilityChanged);
    motion.removeEventListener("change", updateMotion);
    console.error("Living Archive enhancement stopped", error);
  };

  const render = (): void => {
    renderFrameId = 0;
    if (!active || failed || document.hidden) return;
    try {
    const states = mode === "mobile" ? mobileArchiveStates : mode === "tablet" ? tabletArchiveStates : desktopArchiveStates;
    const position = chapterPosition(window.scrollY, anchors);
    const index = Math.min(6, Math.floor(position));
    const progress = position - index;
    const from: ArchivePose = states[index]!;
    const to: ArchivePose = states[Math.min(6, index + 1)]!;
    const global = mix(from.folio, to.folio, progress);
    folio.style.transform = `translate3d(${global[0]}px, ${global[1]}px, 0) scale(${global[2]})`;
    folio.style.opacity = String(global[3]);
    sheets.forEach((sheet, part) => {
      setMaterial(sheet, mix(from.sheets[part]!, to.sheets[part]!, progress));
      sheet.style.setProperty("--archive-sheet-prominence", String(interpolateSheetProminence(from, to, part, progress)));
    });
    vellums.forEach((vellum, part) => setMaterial(vellum, mix(from.vellums[part]!, to.vellums[part]!, progress)));
    tabs.forEach((tab, part) => setMaterial(tab, mix(from.tabs[part]!, to.tabs[part]!, progress)));
    const spinePose = mix(from.spine, to.spine, progress);
    setLine(spine, spinePose);
    const tickExtent = interpolateNumber(from.tickExtent, to.tickExtent, progress);
    ticks.forEach((tick, part) => {
      const fraction = (part + .5) / ticks.length;
      const y = interpolateNumber(spinePose[1], spinePose[3], fraction);
      const length = part % 4 === 0 ? 16 : part % 2 === 0 ? 10 : 6;
      setLine(tick, [spinePose[0] + 2, y, spinePose[0] + length, y,
        fraction <= tickExtent ? spinePose[4] * (part % 4 === 0 ? .9 : .55) : 0]);
    });
    seams.forEach((seam, part) => setLine(seam, mix(from.seams[part]!, to.seams[part]!, progress)));
    setLine(branch, mix(from.branch, to.branch, progress));
    traces.forEach((trace, part) => setPoint(trace, mix(from.traces[part]!, to.traces[part]!, progress)));
    joints.forEach((joint, part) => setPoint(joint, mix(from.joints[part]!, to.joints[part]!, progress)));
    stage.dataset.archivePosition = String(position);
    stage.dataset.archiveState = String(index);
    document.documentElement.classList.add("archive-enhanced");
    } catch (error) {
      rollback(error);
    }
  };
  function queue(): void {
    if (active && !failed && !renderFrameId && !document.hidden) renderFrameId = requestAnimationFrame(render);
  }
  const measure = (): void => {
    measureFrameId = 0;
    if (failed || motion.matches || document.hidden) return;
    try {
      mode = breakpoint();
      anchors = chapters.map(chapter =>
        chapter.getBoundingClientRect().top + window.scrollY + chapter.offsetHeight / 2 - window.innerHeight / 2
      );
      active = true;
      render();
    } catch (error) {
      rollback(error);
    }
  };
  function queueMeasure(): void {
    if (!failed && !motion.matches && !measureFrameId && !document.hidden)
      measureFrameId = requestAnimationFrame(measure);
  }
  function visibilityChanged(): void { if (!document.hidden) queueMeasure(); }
  function updateMotion(): void {
    stop();
    if (!motion.matches && !failed) measure();
  }

  // Validate required geometry and the motion query before installing listeners or hiding SSR snapshots.
  if (!motion.matches) measure();
  if (failed) return;
  try {
    observer = new ResizeObserver(queueMeasure);
    chapters.forEach(chapter => observer!.observe(chapter));
    window.addEventListener("scroll", queue, { passive: true });
    window.addEventListener("resize", queueMeasure, { passive: true });
    document.addEventListener("visibilitychange", visibilityChanged);
    motion.addEventListener("change", updateMotion);
    document.fonts.ready.then(queueMeasure).catch(() => {});
    document.querySelectorAll<HTMLAnchorElement>(".home-nav__mobile nav a").forEach(link => {
      link.addEventListener("click", () => link.closest("details")?.removeAttribute("open"));
    });
  } catch (error) {
    rollback(error);
  }
}
