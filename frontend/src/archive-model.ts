export type SheetPose = readonly [number, number, number, number, number, number];
export type FolioPose = readonly [number, number, number, number];
export type GeometryPose = readonly [number, number, number, number, number];
export type PointPose = readonly [number, number, number];
export type ArchivePose = {
  readonly folio: FolioPose;
  readonly sheets: readonly SheetPose[];
  readonly sheetRoles: readonly number[];
  readonly vellums: readonly SheetPose[];
  readonly spine: GeometryPose;
  readonly tickExtent: number;
  readonly seams: readonly GeometryPose[];
  readonly branch: GeometryPose;
  readonly traces: readonly PointPose[];
  readonly joints: readonly PointPose[];
  readonly tabs: readonly SheetPose[];
};
export type Breakpoint = "desktop" | "tablet" | "mobile";

const desktopMaterialPoses: readonly Pick<ArchivePose, "folio" | "sheets">[] = [
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


const structure: Record<Breakpoint, readonly [number, number, number, number, number, number, number][]> = {
  desktop: [
    [54, 0.1, 0, 0, 0, 0, 0], [126, 0.45, 0.2, 0.15, 0.15, 0.4, 0.1],
    [158, 0.35, 0.55, 0.15, 0.2, 0.2, 0.1], [236, 0.65, 0.65, 0.9, 0.75, 0.6, 0.75],
    [370, 1, 1, 1, 1, 1, 1], [126, 0.4, 0.3, 0.2, 0.25, 0.25, 0.2],
    [42, 0.2, 0.1, 0, 0, 0.1, 0]
  ],
  tablet: [
    [44, 0.12, 0, 0, 0, 0, 0], [108, 0.4, 0.15, 0.1, 0.15, 0.35, 0.1],
    [142, 0.35, 0.48, 0.1, 0.2, 0.2, 0.1], [214, 0.6, 0.65, 0.8, 0.7, 0.6, 0.7],
    [318, 1, 1, 1, 1, 1, 1], [105, 0.35, 0.25, 0.15, 0.2, 0.2, 0.2],
    [35, 0.15, 0.1, 0, 0, 0.1, 0]
  ],
  mobile: [
    [30, 0.12, 0, 0, 0, 0, 0], [95, 0.4, 0.12, 0.1, 0.1, 0.3, 0.1],
    [130, 0.4, 0.38, 0.12, 0.2, 0.2, 0.1], [190, 0.7, 0.6, 0.85, 0.65, 0.6, 0.7],
    [330, 1, 1, 1, 1, 1, 1], [100, 0.35, 0.2, 0.1, 0.2, 0.2, 0.1],
    [28, 0.15, 0.08, 0, 0, 0.1, 0]
  ]
};

const tabletSheets: readonly (readonly SheetPose[])[] = [
  [[0, 0, 1, 1, -2, .78], [0, 0, 1, 1, 1, .86], [0, 0, 1, 1, -1, .73], [0, 0, 1, 1, 2, .7]],
  [[-68, 25, 1.1, 1.05, -2, .9], [92, -20, 1, .9, 2, .7], [88, 72, 1, .9, 1, .8], [-30, 105, .95, .85, -1, .72]],
  [[-35, -8, 1.5, .04, 0, .7], [-20, 54, 1.5, .04, 0, .65], [-42, 116, 1.5, .04, 0, .6], [-25, 178, 1.5, .04, 0, .6]],
  [[-75, 38, .95, .9, -1, .75], [65, -50, .82, .7, 2, .65], [100, 55, .85, .75, -2, .7], [-8, 155, .82, .7, 1, .55]],
  [[-125, 40, 1.15, 1.2, -1, .95], [-20, 82, 1.08, 1.2, 1, .9], [115, 35, 1.15, 1.2, -1, .94], [205, 105, 1.08, 1.1, 1, .9]],
  [[0, -10, .9, .95, 0, .8], [-60, -15, .85, .8, -1, .54], [-70, -8, .82, .8, 1, .25], [-90, 12, .8, .7, 0, .15]],
  [[0, 10, .75, .75, 0, .65], [-20, -5, .7, .7, 0, .2], [-25, 8, .7, .7, 0, .1], [-35, 12, .65, .65, 0, 0]]
];

const mobileSheets: readonly (readonly SheetPose[])[] = [
  [[0, 0, .78, .82, -2, .78], [0, 0, .78, .82, 1, .86], [0, 0, .78, .82, -1, .73], [0, 0, .78, .82, 2, .7]],
  [[-35, -30, .8, .8, -2, .9], [48, 0, .82, .82, 2, .72], [-25, 75, .8, .8, 1, .8], [45, 110, .78, .75, -1, .72]],
  [[-15, -15, 1.1, .04, 0, .7], [-15, 40, 1.1, .04, 0, .65], [-15, 95, 1.1, .04, 0, .6], [-15, 150, 1.1, .04, 0, .6]],
  [[-28, -50, .76, .72, -1, .75], [38, 0, .75, .7, 2, .65], [-27, 95, .76, .72, -2, .7], [40, 160, .75, .7, 1, .55]],
  [[-25, -125, .9, .9, -1, .95], [30, -35, .9, .9, 1, .9], [-22, 65, .9, .9, -1, .94], [35, 160, .9, .9, 1, .9]],
  [[0, -10, .7, .8, 0, .8], [-35, -10, .7, .7, -1, .54], [-40, -8, .7, .7, 1, .25], [-45, 12, .7, .7, 0, .15]],
  [[0, 10, .55, .65, 0, .65], [-15, -5, .55, .6, 0, .2], [-20, 8, .55, .6, 0, .1], [-25, 12, .55, .55, 0, 0]]
];

function buildStates(breakpoint: Breakpoint): readonly ArchivePose[] {
  const mobile = breakpoint === "mobile";
  const tablet = breakpoint === "tablet";
  const centerX = mobile ? 280 : tablet ? 260 : 280;
  return desktopMaterialPoses.map((base, index) => {
    const [originalLength, spineOpacity, seamOpacity, branchOpacity, traceOpacity, jointOpacity, vellumOpacity] = structure[breakpoint][index]!;
    const spineLength = index === 4 ? (mobile ? 350 : tablet ? 400 : 470) : originalLength;
    const centerY = index === 4 ? 310 : 190;
    const reach = mobile ? 66 : tablet ? 104 : 150;
    const density = index === 4 ? 1 : index === 3 ? .65 : index === 1 ? .42 : index === 2 ? .22 : index === 5 ? .25 : .12;
    const sheets = mobile ? mobileSheets[index]! : tablet ? tabletSheets[index]! : base.sheets;
    const folio: FolioPose = mobile ? [0, index === 4 ? -10 : 0, 1, base.folio[3]]
      : tablet ? [base.folio[0] * .5, base.folio[1] * .6, 1, base.folio[3]] : base.folio;
    return {
      folio, sheets,
      sheetRoles: index === 4 ? [3, 2, 3, 2] : index === 3 ? [2, 1, 2, 1] : index === 1 ? [3, 1, 2, 1] : [2, 1, 1, 0],
      vellums: [
        [0, index === 4 ? 28 : 0, index === 4 ? 1.35 : 1, index === 2 ? .08 : 1, 0, vellumOpacity],
        [index === 4 ? (mobile ? 30 : 95) : 0, index === 4 ? 145 : 35, 1, 1, 0, vellumOpacity * (index === 4 ? .8 : index === 3 ? .5 : .2)]
      ],
      spine: [centerX, centerY - spineLength / 2, centerX, centerY + spineLength / 2, spineOpacity],
      tickExtent: index === 4 ? 1 : index === 3 ? .7 : index === 1 ? .48 : index === 2 ? .25 : index === 6 ? .08 : .18,
      seams: [
        [centerX - reach, centerY, centerX + reach, centerY, seamOpacity],
        [centerX - reach * .7, centerY - 95, centerX + reach * .45, centerY - 95, seamOpacity * density],
        [centerX - reach * .35, centerY + 112, centerX + reach, centerY + 112, seamOpacity * density],
        [centerX - reach, centerY + 195, centerX + reach * .55, centerY + 195, seamOpacity * (index === 4 ? .82 : .12)]
      ],
      branch: [centerX, centerY, centerX + (mobile ? 58 : 125), centerY + (mobile ? 130 : 115), branchOpacity],
      traces: [
        [centerX + (mobile ? 58 : 125), centerY + (mobile ? 130 : 115), traceOpacity],
        [centerX - reach * .7, centerY - 95, traceOpacity * density],
        [centerX + reach * .55, centerY + 195, traceOpacity * (index === 4 ? .75 : .08)]
      ],
      joints: [
        [centerX, centerY, jointOpacity],
        [centerX, centerY - 95, jointOpacity * density],
        [centerX, centerY + 112, jointOpacity * density],
        [centerX, centerY + 195, jointOpacity * (index === 4 ? .9 : .1)]
      ],
      tabs: [
        [-8, 96, 1, 1, 0, index === 4 ? .95 : .45],
        [mobile ? 205 : 375, 150, 1, 1, 0, density * .8],
        [-8, 320, 1, 1, 0, index === 4 ? .8 : .1],
        [mobile ? 205 : 375, 395, 1, 1, 0, index === 4 ? .65 : .06]
      ]
    };
  });
}

export const desktopArchiveStates = buildStates("desktop");
export const tabletArchiveStates = buildStates("tablet");
export const mobileArchiveStates = buildStates("mobile");

export function interpolateNumber(a: number, b: number, progress: number): number {
  return a + (b - a) * progress;
}
export function chapterPosition(scrollY: number, anchors: readonly number[]): number {
  if (anchors.length !== 7) throw new Error("The Archive requires seven chapter anchors");
  if (scrollY <= anchors[0]!) return 0;
  for (let index = 0; index < anchors.length - 1; index++) {
    if (scrollY <= anchors[index + 1]!) {
      const distance = anchors[index + 1]! - anchors[index]!;
      return index + (distance > 0 ? Math.max(0, Math.min(1, (scrollY - anchors[index]!) / distance)) : 1);
    }
  }
  return 6;
}
