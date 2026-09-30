type SheetPose = readonly [x: number, y: number, scaleX: number, scaleY: number, angle: number, opacity: number];
type ArchivePose = {
  readonly folio: readonly [x: number, y: number, scale: number, opacity: number];
  readonly sheets: readonly SheetPose[];
};

const poses: readonly ArchivePose[] = [
  {
    folio: [0, 0, 1, 0.86],
    sheets: [[0, 0, 1, 1, -2, 0.78], [0, 0, 1, 1, 1, 0.86], [0, 0, 1, 1, -1, 0.73], [0, 0, 1, 1, 2, 0.7]]
  },
  {
    folio: [-105, 28, 1.13, 0.82],
    sheets: [[-116, 68, 1.27, 1.12, -2, 0.9], [126, -12, 1.08, 0.94, 2, 0.7], [116, 105, 1.1, 0.95, 1, 0.8], [-58, 145, 1.05, 0.9, -1, 0.72]]
  },
  {
    folio: [-75, 12, 1.12, 0.45],
    sheets: [[-55, -20, 1.85, 0.035, 0, 0.7], [-30, 72, 1.7, 0.035, 0, 0.65], [-64, 156, 1.8, 0.035, 0, 0.6], [-42, 240, 1.7, 0.035, 0, 0.6]]
  },
  {
    folio: [-65, 5, 1.04, 0.78],
    sheets: [[-102, 48, 1.02, 0.95, -1, 0.75], [78, -68, 0.8, 0.75, 2, 0.65], [126, 70, 0.85, 0.78, -2, 0.7], [-8, 210, 0.85, 0.7, 1, 0.55]]
  },
  {
    folio: [-230, 95, 1.52, 0.98],
    sheets: [[-182, 30, 1.28, 1.28, -1, 0.95], [-20, 80, 1.12, 1.34, 1, 0.9], [175, 25, 1.27, 1.33, -1, 0.94], [315, 120, 1.2, 1.22, 1, 0.9]]
  },
  {
    folio: [0, -10, 0.83, 0.58],
    sheets: [[0, 0, 1.1, 1, 0, 0.8], [-90, -20, 0.9, 0.85, -1, 0.54], [-100, -10, 0.86, 0.8, 1, 0.25], [-130, 15, 0.82, 0.75, 0, 0.15]]
  },
  {
    folio: [100, 30, 0.39, 0.42],
    sheets: [[0, 0, 1, 1, 0, 0.65], [-34, -12, 0.82, 0.82, 0, 0.2], [-40, 8, 0.8, 0.8, 0, 0.1], [-50, 12, 0.75, 0.75, 0, 0]]
  }
];

function interpolate(a: number, b: number, progress: number): number {
  return a + (b - a) * progress;
}

function clamp(value: number): number {
  return Math.max(0, Math.min(1, value));
}

export function enhanceArchive(): void {
  const stage = document.querySelector<HTMLElement>(".archive-stage");
  const folio = document.querySelector<HTMLElement>("[data-archive-folio]");
  const sheets = Array.from(document.querySelectorAll<HTMLElement>("[data-archive-sheet]"));
  const chapters = Array.from(document.querySelectorAll<HTMLElement>("[data-archive-chapter]"));
  if (!stage || !folio || sheets.length !== 4 || chapters.length !== poses.length) {
    return;
  }

  const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
  let boundaries: number[] = [];
  let queued = false;

  const measure = (): void => {
    boundaries = chapters.slice(1).map((chapter) => chapter.getBoundingClientRect().top + window.scrollY);
    queue();
  };

  const render = (): void => {
    queued = false;
    if (motionPreference.matches || document.hidden) {
      return;
    }
    let position = 0;
    for (let boundary = 0; boundary < boundaries.length; boundary++) {
      const start = boundaries[boundary]! - window.innerHeight * 0.9;
      const end = boundaries[boundary]! - window.innerHeight * 0.1;
      if (window.scrollY < start) {
        break;
      }
      position = boundary + clamp((window.scrollY - start) / (end - start));
      if (window.scrollY < end) {
        break;
      }
    }
    const index = Math.min(Math.floor(position), poses.length - 1);
    const progress = position - index;
    const from = poses[index]!;
    const to = poses[Math.min(index + 1, poses.length - 1)]!;
    const folioPose = from.folio.map((value, part) => interpolate(value, to.folio[part] ?? value, progress));
    const architectureWeight = Math.max(0, 1 - Math.abs(position - 4));
    const architectureOffset = window.innerWidth <= 672 ? -250 : window.innerWidth <= 1024 ? -100 : 95;
    folio.style.transform = `translate3d(${folioPose[0]}px, ${folioPose[1]! + architectureOffset * architectureWeight}px, 0) scale(${folioPose[2]})`;
    folio.style.opacity = String(folioPose[3]);
    sheets.forEach((sheet, part) => {
      const pose = from.sheets[part]!.map((value, property) =>
        interpolate(value, to.sheets[part]![property] ?? value, progress)
      );
      sheet.style.transform = `translate3d(${pose[0]}px, ${pose[1]}px, 0) scale(${pose[2]}, ${pose[3]}) rotate(${pose[4]}deg)`;
      sheet.style.opacity = String(pose[5]);
    });
  };

  const queue = (): void => {
    if (!queued && !motionPreference.matches && !document.hidden) {
      queued = true;
      requestAnimationFrame(render);
    }
  };

  const updatePreference = (): void => {
    document.documentElement.classList.toggle("archive-enhanced", !motionPreference.matches);
    if (motionPreference.matches) {
      folio.removeAttribute("style");
      sheets.forEach((sheet) => sheet.removeAttribute("style"));
    } else {
      measure();
    }
  };

  window.addEventListener("scroll", queue, { passive: true });
  window.addEventListener("resize", measure, { passive: true });
  document.addEventListener("visibilitychange", queue);
  motionPreference.addEventListener("change", updatePreference);
  document.querySelectorAll<HTMLAnchorElement>(".home-nav__mobile nav a").forEach((link) => {
    link.addEventListener("click", () => {
      link.closest("details")?.removeAttribute("open");
    });
  });
  updatePreference();
}
