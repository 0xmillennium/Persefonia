# Identity effects — Experiment 04

One segmented `0xmillennium` identity, one custom handwritten `M.` with a turquoise writing point and letter-body glitter, two trigger modes. This standalone Vite experiment does not integrate with the production homepage.

From `frontend/` (Node ≥22.12, existing npm dependencies):

```sh
npm run effects:dev
```

Open <http://127.0.0.1:4284/>. Loading Mode loops through eight authored movements; Pause returns the mark to rest. Hover Mode plays once per pointer entry, holds the signed rectangle, then recovers on leave. Focus the mark and press Enter/Space, or tap, to toggle inspection; Escape or focus departure resets it. Mode changes recover smoothly, including mid-cycle.

Reduced motion removes the opening/rotation variations, pen travel and glitter sweep, gently closes the original gaps, fades in the finished `M.` with its static turquoise undertone, and extends loading holds. Entering reduced motion immediately restores baseline before the quiet cycle, removing residual motion. Hidden tabs cancel work and restore baseline; loading restarts when visible. No JavaScript or unavailable WAAPI leaves the approved static emblem and explanatory content.

```sh
npm run effects:check
npm run effects:browser
EFFECTS_BUILT=1 npm run effects:browser
npm run effects:preview # built page at http://127.0.0.1:4285/
```

`EFFECTS_REUSE_SERVER=1` allows browser checks against an already running matching server. Build before testing or previewing the built page. Playwright's Chromium, Firefox and WebKit must already be installed.

The baseline polygons are generated **directly** from `../segmented-identity/geometry.ts`'s approved `canonicalContours`. Only the generator imports that module; no physics enters the browser bundle. All eight contours close with zero translation/rotation into the original 116 × 160 rectangle. A same-size backing fills antialias seams only after closure. The independent calligraphic `M.` follows the user's supplied lettering image, retained unchanged in `assets/reference-lettering.png` for source provenance. Its smoothed vector outline preserves the large left flourishes, sloping joins and high right loop. The bitmap is not a runtime asset; the pieces never form the letter.

Five authored nib paths follow the four supplied writing checkpoints: small inner oval and first tall stroke; large outer-left loop; nested left flourishes, M joins and cross-sweep; then the trailing right stroke. The unchanged intermediate images are `assets/reference-stage-1.png` through `reference-stage-3.png`; `reference-lettering.png` is the finished reference. The pen in the third checkpoint obscures part of an already visible right loop, so it is not treated as absent. The photos establish cumulative additions; within-stage direction and repositioning lifts remain inferred.

Each turquoise point follows its corresponding ink path using CSS motion path. A continuous 0.35-unit hairline restores the small oval where the earlier bitmap trace broke up. A static ownership mask keeps later flourishes out of that first stroke. Writing uses a 1100 ms proportional budget with a 120 ms minimum per stroke, plus 70 ms hidden repositioning between strokes. After another 110 ms lift, the period writes separately over 160 ms beside the final tail, with its nib and glitter using the same position. Inactive strokes stay transparent until their turn. Once the full ink is covered, the temporary writing mask is removed to avoid softening fine edges. A single 680 ms turquoise reflection crosses the inscription. No font, particle system, perpetual shimmer or JavaScript frame loop is used.

`src/choreography.ts` defines aperture, counterturn, diagonal relay, rail lock, quarter index, **front flip**, **back flip** and **floor bounce**. Rail lock registers X before Y; quarter index turns the eight-piece field through 90°. The flips use opposite full 360° rotations around the horizontal axis, an airborne arc, launch compression and landing recovery. Floor bounce falls to a lower contact plane, compresses at impact, stretches into the rebound and settles back into position. A temporary floor shadow gives all three physical moves a contact reference. Native CSS perspective on an HTML wrapper supplies depth without a rendering engine. Travel is proportional to the viewport and adapts during resizing. Reduced motion omits these movements.

`src/animation.ts` owns bounded WAAPI effects, samples interrupted rendered poses (including SVG attributes omitted from WebKit's computed CSS), and preserves signed rotation winding. Recovery resets the segment field, perspective wrapper, shadow, reflection, nibs and ink. `src/controller.ts` owns trigger intent, abortable tasks and explicit baseline → active → closing → closed → reveal → resolved → reset states. Both modes share the same engine, endpoint and variant order. Held hover has no running animation, timer or RAF loop. Self-hosted external files preserve a strict standalone CSP.

`page.html` is the source template; `index.html` is generated and checked at build. After an intentional geometry, template or mark change:

```sh
npm run effects:geometry
```

See [REPORT.md](REPORT.md) for observed qualification, design-adviser use and limitations. This is an animation study, with no backend load tracking or fake progress percentage.
