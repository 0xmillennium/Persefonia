# Experiment 02 — navigator material refinement

> This checkpoint is superseded for hold behavior, touch clock, compatibility compositing and qualification by [HOLD.md](HOLD.md). Shared Chrome compatibility capture filenames were regenerated during that pass; its current evidence and source fingerprints belong to HOLD.md. This document retains the preceding material/transport measurements as historical evidence.

2026-10-12, Europe/Istanbul. **Verdict: NEEDS_REWORK.** The bounded navigator implementation is reviewable; this is not completed G8 or production qualification. The user explicitly approved keeping the charcoal robotic-arm background and rejected the preceding navigator. This report supersedes FOLLOWUP's navigator material conclusions, not its source-integrity evidence or historical transport failures.

## A. Repository state

Starting and final branch: `feat/scroll-film-navigator`; HEAD remains `fa3aefe8b79a14751c47ddfb178783627a48fb3b`. The existing task's uncommitted work was preserved. No commits, pushes, dependencies or production integration were added.

This refinement changes `src/{input,field,gpu-fluid,material,canvas-renderer,main}.ts`, adds `src/pigment.ts`, extends `tests/{field,support}.ts`, adds `tests/{pigment,navigator}.ts`, includes the navigator case in `playwright.config.ts`, and updates README, QUALIFICATION, FOLLOWUP and this report. Other dirty files belong to the preceding authorized finalization/follow-up; they were not discarded.

The complete final working-tree inventory is [working-tree.txt](../../test-results/scroll-film-navigator/navigator-refinement/working-tree.txt). Final `git diff --check` and current-source built cheap preflight passed.

Canonical 384 source PNGs and the supplied reference video were not modified. No preparation/publication was run this pass. The accepted charcoal publication remains identity `1cc6d4a6616442c9557b9b434630f0b86ef76b2aeac91c86ccb6ff3502f79a0d`; source preservation and unchanged tree inventory are established by FOLLOWUP's actual full validation. Runtime source fingerprint at final recordings/build: `ce402f9045ad5252ddd8e99865a892060b531c72b8721853a81766d95dbd8744`.

## B. What was actually fixed

**Navigator.** The previous compatibility field retained scalar density and composited a flat ribbon, with no transported pigment colors or film refraction. Comparing HEAD showed the GPU material itself had not changed in that rejected checkpoint: it still used regular cross-stroke bands and additive highlights. These are verified implementation gaps, not a claim to have reproduced the exact user's Brave profile.

A shared material now specifies 48→14 CSS-pixel speed-sensitive radii, longer directional wakes, irregular spatial bands/veins, stronger transported velocity, bounded absorption and local displacement. Canvas transports three channels with pressure projection, vorticity confinement and clamped MacCormack correction. Its dye grid is bounded to 192×256, velocity uses half the dimensions, and field arrays remain below 2.5 MB. Resident film pixels are cached lazily and released with their renderer; refraction/output allocation is reused. GPU dye interpolation makes optical slopes continuous between stored texels. Convex pigment/absorbed-film blending avoids the prior additive fluorescent clipping on the light tree scene. Hold mask/reveal semantics were retained.

There were three bounded, evidence-driven material corrections: coherent transport/seeding, optical interpolation after observed displacement stepping, and absorption after inspecting overbright tree pixels. The interpolation correction first proved the diagnosis with manual interpolation, then used equivalent hardware filtering to avoid unnecessary texture reads. No new renderer framework was introduced.

**Scroll.** User-approved wheel calibration and native scroll-to-frame mapping were retained. No claim that this refinement repairs the known warm 1×/2× delivery failure.

**Media fidelity.** The approved arm background is retained unchanged. No new extraction or source-detail-recovery claim. Earlier restoration comparisons remain scoped to the same publication.

**Themes.** Dark arm/light tree semantics are retained. Current finalization cases cover semantic selection/theme linkage, suspension and equal-clock movement.

**Graphics performance.** No physical integrated GPU is available in this VM. Actual final recordings show poor pacing on SwiftShader and an expensive Canvas compositor. Bounded allocations and stopped idle RAF do not constitute a timing pass.

**Test orchestration.** Cheap preflight consumes existing validated assets; preparation remains explicit. Current navigator tests measure speed-dependent width, post-stop spatial evolution, pixels, crisp-core tracking and complete decay on both films. Touch projects skip that fine-pointer-only case. Tests were not weakened to conceal failures.

## C. Test-execution efficiency

- Focused field/motion subsystem: 18 passed, 6.51s after the initial resource-bound/CPU-duration failure. CPU resolution and repeated interpolation arithmetic were corrected; timeout was not raised. Two absorption/seed units passed, 205ms. Final combined targeted run: **20 passed, 7.14s**, and typecheck passed.
- Navigator browser cases: **2 passed**, approximately 1.6min, covering GPU/Canvas and both films. The initial pair failed because a frozen test clock awaited an unadvanced tree reveal; the fixture was corrected. Subsequent GPU-only runs validated manual optical interpolation and its equivalent hardware-filtered implementation, approximately 1.3min and 1.2min. These reruns addressed observed changes, not a full-suite loop.
- Final absorption source: **4 finalization cases passed, 23.1s** (rounded-clock geometry on both paths, suspension, theme switching). The absorption unit asserts bounded color channels. Final native recordings and Chrome captures were regenerated after this change.
- Current built no-JS and reduced-motion/keyboard/self-hosted cases: **2 passed, 3.3s**. Build passed (20 modules, 309ms; JS 64.72 kB / 20.79 kB gzip).
- Native stationary-touch modality case: **1 passed, 2.3s**. Existing native touch re-press/pan case: **1 failed, 3.8s**, at the retained-progress assertion (0.23047 observed; ≥0.24842 expected). It stopped before its pan assertions. Cause is not established; slow software rendering alone is not proof of a flaky test. No weakening or speculative hold rewrite was made.
- One attempted touch selection matched no tests; the corrected selection produced the explicitly reported failure above.
- Final native GPU capture: COMPLETE, 39.176s timeline / 39.72s encoded video. Final compatibility capture: COMPLETE, 16.675s timeline / 17.64s video. Source fingerprints matched before/after.
- **Full functional gates: 0; heavy performance/soak: 0; media preparation: 0 this pass.** Physical-device qualification and final G8 remain unexecuted. No repeated full hashing/768-frame decode or test nesting.

## D. Visual evidence

Current-source artifacts are in [navigator-refinement/](../../test-results/scroll-film-navigator/navigator-refinement/):

- [Native GPU recording](../../test-results/scroll-film-navigator/navigator-refinement/native-gpu.webm), [timeline](../../test-results/scroll-film-navigator/navigator-refinement/native-gpu-timeline.json), [contact sheet](../../test-results/scroll-film-navigator/navigator-refinement/gpu-final-contact.png): slow/fast gestures, crossing, decay, wheel step, dark/light switch, early cancellation, resumed growth, outward reveal and long-hold single commit. Named screenshots are in [review/](../../test-results/scroll-film-navigator/finalization/review/). The timeline confirms one committed transition through the extended press; screenshots alone do not approve moving-rim quality.
- [Native compatibility recording](../../test-results/scroll-film-navigator/navigator-refinement/native-compatibility/navigator.webm), its timeline and [contact sheet](../../test-results/scroll-film-navigator/navigator-refinement/compatibility-final-contact.png): arm/tree interaction, post-stop curl, settled field and reduced motion.
- `chrome-{gpu,compatibility}-native-{clean,painted,post-stop,decayed}.png` and matching JSON: regenerated in actual Chrome 154, 40 trusted native mouse injections per path; fields settled and scheduling stopped. Gesture timing includes IPC/capture overhead and cannot establish requested physical pointer speeds.
- `chrome-mcp-final-arm.png`: actual Chrome DevTools MCP final screenshot. The MCP was accessed through the installed official wrapper/SDK because this session had no directly registered Chrome namespace. Both pages were inspected using newly listed session-local IDs.
- `gpu-measurements.json` and `compatibility-measurements.json`: controlled transport/width evidence from the material before the final absorption-only change. They are not mislabeled final color captures. GPU slow/fast transverse-variance ratio was 5.37; post-stop components changed from 2 to 7 on arm and 2 to 4 on tree, with no new injections. Those observations support evolution/splitting but do not alone prove every organic merging aesthetic.

Directly inspected: both final six-image contact sheets, final full-size Chrome pigment captures on both paths, the reference contact sheet and 12-frame temporal curl strip. Generated recordings are available for playback; selected snapshots/metadata inspection is not a claim that full playback was watched.

Reference: `../sample-site-analysis/navigator effect.mp4`, H.264 1858×1006, 30fps, 20.366633s; SHA-256 `106ea467456b95fdc9b8c608f45615027bade7be13bbd3ab23bd2430446b7f84`. Timestamped frames show broad lagging curls, irregular islands and stronger local refraction. Transfer those interaction principles into the requested turquoise material; do not claim the reference's hidden implementation is known.

One bounded Local Codex advisory run (`14fc0cf0-acc0-436a-be2f-5244f9906236`, 76.253s) produced an inspected 450-word critique in `/home/enes/tools/open-design/.od/projects/experiment02-navigator-fidelity-82cad9e2-22e4/critique.md`. It read four targeted implementation files and two captures, performed no repository edits/media generation/tests, and advised transported channels, irregular bands and cached local refraction. Registered transport failures were resolved through the installed local MCP wrapper; they were not reported as successful calls.

Warm 1×/2× traversal and restoration comparisons referenced in FINALIZATION/FOLLOWUP are historical. No current-source traversal/performance pass was regenerated or substituted.

## E. Performance evidence

Hardware: VMware `vmwgfx`, PCI `15AD:0405`; software-rendered VM, not physical Intel/AMD integrated graphics. Final native browser: Chrome `154.0.8037.97`, ANGLE/Vulkan SwiftShader, 1200×800, DPR 1. Review videos: Chromium `143.0.7499.4`, 1200×800 VP8/25fps. Physical display refresh/scanout is NOT VERIFIED.

Workload: one 40-segment figure-eight trusted pointer gesture, 400ms post-stop capture, then 4.5s settlement; native CDP diagnostics include screenshot/IPC overhead. GPU intervals: median **116.6ms**, p95 **166.7ms**; JS median 0.3ms / p95 1.7ms. Compatibility intervals: median **49.9ms**, p95 **66.7ms**; JS median 40.6ms / p95 66.4ms. Neither establishes acceptable smoothness. No claim of 60fps or accelerated-hardware performance.

Tracked GPU renderer estimate: 16,464,896 bytes (including 6,766,592 field bytes). Compatibility renderer: 27,060,000 bytes plus 1,179,648 field bytes at this viewport. Decoded film cache remains bounded to 20 bitmaps / 115,200,000 pixel bytes at 1600px. These are allocation estimates, exclude browser/driver overhead, and are not measured total RAM/VRAM or an endurance trend.

Both paths drew arm frame 144, retained bounded queues and settled to inactive/no RAF after final gestures. Decode readiness and captured warm 1×/2× film continuity were not benchmarked here. Lower CPU simulation resolution trades some detail for bounded work; screenshots show curls/refraction, but Canvas still has substantial compositor cost. Hardware filtering avoids the intermediate manual interpolation's extra reads without intentionally reducing the optical result. No visual quality was sacrificed to claim a timing pass.

## F. Qualification matrix

| Requirement | Status | Evidence / limit |
| --- | --- | --- |
| Isolation, immutable source, retained approved arm delivery | PASS | Scoped changes; unchanged publication; preceding integrity receipt |
| Visible turquoise movement on both films/renderers | PASS | Final native pixels/recordings; actual Chrome gestures |
| Slow broader / fast thinner directional wake | PASS | Controlled width regression; native requested speeds are not actual-speed measurements |
| Spatial memory, post-stop advection and curl | PASS | Field evolution and changed pixels without fresh input; inspected curls |
| Organic veins, merges/splits, tendrils and artistic fidelity | PARTIAL | Three channels and visible curling/islands; final human acceptance absent |
| Local refraction, crisp independent core, no stationary aura | PASS | Final captures; core and decay regression |
| Few-second decay and stopped idle rendering | PASS | Both controlled and final native settlement |
| Touch excludes pigment / removes hover core | PASS | Current native stationary-touch case |
| Touch re-press residue / native pan | FAIL | Current retained-progress assertion failed before pan verification; cause unclassified |
| Reduced motion / no-JS / keyboard / self-hosted build | PASS | Current targeted built checks and native reduced-state captures |
| Hold cancel/resume/outward reveal/single commit | PARTIAL | Current GPU recording/timeline and targeted cases; touch residue failure; no final rim approval |
| Wheel sensitivity / native film mapping | PASS | Retained user-accepted behavior; final recording's one-frame step |
| Warm 1×/2× and rapid-reversal delivery | FAIL | Prior unresolved captured failure; no replacement current-source pass |
| Robot background and film-linked themes | PASS | Explicit user background approval; unchanged publication; current captures/theme test |
| Complete segmentation or recovered source detail | NOT VERIFIED | Never claimed; current treatment is a contrast matte |
| Exact user's Brave/Chrome profile cause | NOT VERIFIED | Actual isolated Chrome inspected; user profiles not reproduced |
| Smooth current local GPU/Canvas motion | FAIL | Final observed pacing above |
| Physical integrated-GPU/mobile timing and endurance | NOT VERIFIED | Suitable hardware unavailable |
| Complete engine matrix, source-frozen G8, current soak | NOT VERIFIED | Not executed in this bounded refinement |
| Final human navigator approval | NOT VERIFIED | User requested correction; approval not yet supplied |

## G. Final verdict

**NEEDS_REWORK.** The rejected flat navigator now has a reviewable shared liquid material and current-source evidence while preserving the approved arm background. Known pacing/film-continuity failures and the observed touch re-press assertion prevent a full qualification pass. No further speculative repair or heavy loop was launched beyond the bounded material corrections. Preview: `http://127.0.0.1:4180/`.
