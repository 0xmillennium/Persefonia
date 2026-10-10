import source from "./legacy-geometry.json";
import { fragmentGeometry } from "../geometry-loader";

// Captured deadlock regressions depend on the original nine-piece contours,
// masses and anchors. Keep those fixtures executable when the visual mark changes.
export const legacyGeometry = source.fragments.map(f => fragmentGeometry(f.id,
  f.vertices.map(([x, y]) => ({ x: x!, y: y! })), f.anchorIndices));
