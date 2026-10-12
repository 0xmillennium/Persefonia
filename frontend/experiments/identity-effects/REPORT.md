# Experiment 04 — qualification report

Date: 2026-10-13. Verdict: **READY_FOR_HUMAN_REVIEW**.

## Scope and reference

Checkout: `feat/scroll-film-navigator`, HEAD `c64cd17`. Changes remain confined to this experiment and seven `effects:*` scripts in `frontend/package.json`. Production, prior experiments, dependencies and lockfiles remain unchanged. No commit, push or production integration occurred. Generated builds and browser evidence follow the existing ignored frontend directories.

The user's four photographs now govern the handwriting checkpoints, in this order:

| Stage | Retained source | Visible additions |
| --- | --- | --- |
| 1 | `assets/reference-stage-1.png` — 003850 | Small inner oval and first tall stroke |
| 2 | `assets/reference-stage-2.png` — 003901 | Large outer-left loop |
| 3 | `assets/reference-stage-3.png` — 004005 | Nested left flourishes, M joins, cross-sweep and visible right-loop work |
| 4 | `assets/reference-lettering.png` — 000505 | Complete mark and trailing right stroke |

All four source images are retained unchanged and excluded from the runtime build. The pen is ignored as content; its occlusion is not treated as absent ink. In particular, parts of the right loop are already visible in stage three. The photos establish cumulative additions, while within-stage travel, hidden closure and repositioning lifts remain inferred. The independently requested final period follows stage four. The latest requested adjustment is 0.1 cm upward and 0.05 cm rightward, converted from CSS centimetres using the observed 1.025 CSS px per SVG unit at the 1280 × 800 preview viewport. This adds approximately 3.780 CSS px upward and 1.890 CSS px rightward there; the period now starts at (27.343672, 6.312656). The existing responsive SVG continues to scale this placement with the logo. The reveal path, nib and reflection mask share that position.

## Writing and interaction

Five native SVG nib paths replace the earlier inferred order. The initial oval/riser precedes the outer-left loop; nested work and M joins follow; the right tail finishes last. The original traced outline data is retained. A continuous 0.35-unit oval hairline repairs fragmentation exposed by the early-stage paint check. A static ownership mask restricts the initial stroke, and the outer-loop centreline is fitted more closely to its ink. These deliberate refinements prevent early dot/flourish fragments and preserve completed coverage.

Each turquoise nib follows the corresponding reveal curve and progress. Writing uses a 1100 ms proportional budget with a 120 ms minimum per stroke, four hidden 70 ms repositioning gaps, a 110 ms lift, and a separately written 160 ms period. The minimum gives the short right tail readable pen travel. The temporary mask is removed only after complete ink coverage; one finite 680 ms turquoise reflection then crosses the ink itself.

Loading and Hover share the same SVG/native-WAAPI engine and all eight existing movements. Front/back flips, floor bounce, approved baseline contours and layout are unchanged. Hover runs once per entry and holds without running animations or repeat timers. Leave, reentry and mode changes recover cleanly; Enter/Space/tap, Escape and focus departure provide keyboard/touch inspection. Reduced motion omits physical movements, nib travel and reflection, immediately clears residual pitch/shadows, then gently closes and fades in the inscription. Static semantic HTML retains the approved emblem without JavaScript or WAAPI. Strict CSP and self-hosted external assets remain in place. No framework, rendering engine, particle system or per-frame JavaScript animation loop was introduced.

## Local Codex advice

The fresh staged-reference review completed through the registered local MCP in project `persefonia-e04-reference-review-fresh-a001`, run `ea6127d0-d899-4a52-9595-7f334dbbebab`, using `emil-design-eng` and `review-animations` with the project's existing critique context. Both actual deliverables, `writing-stages-review.md` and `stages-nib-candidate.svg`, were inspected.

The review separated observed additions from inferred travel and proposed five centreline paths. Browser checkpoint inspection exposed fragments in the initial oval and early crossing ink. Main implementation deliberately restored the hairline, bounded initial ownership, adjusted the outer loop and extended the later nested return before requalification. The advice did not mutate repository files and did not provide live-browser approval. Earlier direction, letter studies and final quality reviews remain historical context. No Cloud workflow, direct HTTP bypass or automatic tool-to-source synchronization was used.

## Observed validation

- Typecheck, generated-entry freshness and isolated Vite build passed. JavaScript: 14.16 kB / 4.58 kB gzip. This is transfer-size evidence, not measured CPU/GPU frame delivery.
- Painted checkpoints were captured and inspected in Chromium, Firefox and WebKit. Pixel checks confirm the first riser without the outer/nested loops, dot, high-right loop or tail; the outer loop appears second without the later additions; nested work, dot and visible right-loop work appear third without the tail; the tail appears fourth. Blank plate captures have at most four channel levels of variation, without visible ink. The initial oval is continuous. Shared intersections may reveal touching ink locally.
- Native path sampling checked 11,096 points of the retained outline against the final reveal curves and initial ownership mask. All are covered, including the 0.075-unit half-width of the ink's edge stroke; the minimum sampled radial margin is 0.0877 units. The added oval hairline follows its reveal curve exactly. Sampling is geometric evidence, not proof of every raster sample. Drawing time is approximately 1179 ms before the repositioning gaps and final period.
- Nine focused built-browser writing, reflection and interruption checks passed across Chromium, Firefox and WebKit after the staged-writing refinement. Six writing/reflection checks passed across the same engines for the preceding period placement, including period spacing and nib-to-ink alignment. The latest centimetre adjustment passed typecheck, generated-entry freshness, the isolated build and three writing checks across Chromium, Firefox and WebKit, including period spacing and nib-to-ink alignment. No new full-suite pass is claimed.
- Earlier qualification covered all eight variants, exact closure/held rest/reset, responsive resizing, keyboard/touch, loading/pause and static fallbacks. Phone movement envelopes and live reduced-motion clearing passed in all three engines before this lettering-only refinement; the eight movement choreographies and page layout remain unchanged.

Current stage captures and geometry evidence are in `frontend/test-results/identity-effects/review/`, including `writing-stage-0` through `writing-stage-4` for each engine and `stages-qualification.json`. Earlier motion evidence remains there. `git diff --check`, the text-file whitespace scan and isolated scope inspection passed.

## Limits

Still photographs cannot establish every physical pen movement or hidden stroke. The restored hairline is a deliberate vector refinement of the low-resolution reference, rather than a claim of pixel-identical reproduction. Qualification uses local headless engines, viewport resizing and emulated touch; physical devices, representative frame delivery, genuine hidden-tab transitions and BFCache remain unqualified. Tiny 16–32 px icon use is outside this experiment's qualification. There is no backend loading integration. Human judgement of the handwriting and motion remains necessary.
