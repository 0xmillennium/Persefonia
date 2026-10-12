# Experiment 02 — navigator and charcoal-background follow-up

> Subsequent user feedback approved the charcoal arm background ("background seems nice keep it") and rejected this checkpoint's navigator fidelity. [NAVIGATOR.md](NAVIGATOR.md) records the later reference-video-driven material correction. This report's input/media evidence remains scoped to its checkpoint.

> The shared `finalization/review/` output directory was regenerated during that refinement. Its current recording, timeline and named screenshots belong to NAVIGATOR.md; this report's older 37.04s recording description is historical.

2026-10-12, Europe/Istanbul. **Verdict: NEEDS_REWORK.** This report supersedes the input/media conclusions of [FINALIZATION.md](FINALIZATION.md), while retaining its unresolved transport/performance evidence. It does not claim completed G8 or production readiness.

The user accepted scroll speed, rejected navigator visibility in both Brave profiles and Chrome, rejected the previous restoration, proposed a dark background, delegated the choice, and instructed autonomous investigation without further questions. That authorizes this implementation and publication; it does not establish human artistic approval of the result.

## A. Repository state

Branch and HEAD remain `feat/scroll-film-navigator`, `fa3aefe8b79a14751c47ddfb178783627a48fb3b`. The preceding checkpoint's uncommitted work was retained. Nothing was committed or pushed. No dependencies or lockfiles were added. Production sources and Experiment 01 remain outside the change scope.

Additional follow-up files: `src/input.ts`, `src/canvas-renderer.ts`, `src/main.ts`, `tests/motion.test.ts`, `tests/finalization.spec.ts`, `scripts/background-study.py`, `scripts/background-transform.py`, `scripts/media-config.mjs`, `scripts/prepare.mjs`, `scripts/review-capture.mjs`, README, QUALIFICATION, FINALIZATION and this report. The complete preceding change list is in FINALIZATION A.

All 384 canonical PNGs were fully validated before publication; canonical identity was checked again afterward and remained unchanged. The 768 delivery images passed full pixel decoding and inventory checks. Tree derivatives are byte-for-byte identical to the previous publication in both tiers.

Source inventory: `b51a02afed8301ea4a1b6dede1669ba7c183175776c9499903ccc75bd7baf100`.

Active publication: `1cc6d4a6616442c9557b9b434630f0b86ef76b2aeac91c86ccb6ff3502f79a0d`, preparation version 4. The earlier version remains available as an immutable comparison, rather than being overwritten.

## B. What was actually fixed

**Navigator input:** inspection identified a definite vulnerability: different pointer positions with an equal timestamp were discarded. `PointerObserver` now preserves equal-time geometry, estimates velocity using the last positive interval (initially 1/60 second), rejects backward timestamps, and still ignores duplicate positions. The regression failed before the fix and passes afterward. This estimate is not a measurement of exact speed under a quantized clock. Privacy-related clock rounding is a plausible trigger, not an established cause in the user's profiles.

**Navigator fallback:** Canvas2D previously omitted navigator pigment entirely. It now composites the existing bounded CPU liquid field, with persistence and decay, when float WebGL targets are unavailable. It retains touch/reduced-motion gates and reports compatibility fidelity explicitly. It does not emulate film refraction. Reused pixel storage and offscreen storage are included in resource estimates and disposal. The primary GPU shader was not rewritten. The default recording browser selected compatibility mode; the GPU recording required explicit software-GPU selection locally.

**Observed visibility:** current Chromium pixel regressions pass for GPU and compatibility paths with 24 equal-clock injections. A separate native wall-clock gesture in the actual Chrome DevTools browser produced 32 injections and visible turquoise pigment on the updated arm film. Both-film GPU residue/decay assertions also pass. These establish visible output in tested conditions, not exact reproduction of the user's browser state or acceptance of marbling quality.

**Robotic-arm media:** the rejected low-amplitude restoration was replaced by a deterministic charcoal background treatment across all 192 frames. A robust smooth estimate of the original dark plate drives a continuous contrast matte. Bright/high-contrast pixels are preserved exactly at the 1600px working stage before encoding; low-contrast tones change. This is not a complete semantic foreground extraction and cannot recover source detail. No expanded binary mask is used, avoiding the halos seen in pilot wireframes. All 192 pre-encoding protection assertions pass. Mean near-flat treated background fraction is approximately 83%.

Arm delivery uses WebP q96, with FFmpeg Lanczos to 1600px and Pillow Lanczos downsampling to 960px. Operator SHA-256, options and actual Python/library versions enter publication identity. Tree delivery retains the previous Lanczos q82 files, individually hash-verified before copying. New arm delivery totals 5,128,322 bytes at 960px and 12,640,210 at 1600px, versus 3,051,008 and 7,735,904 previously: +6,981,620 bytes across both tiers. Browser decoding cost of that increase is not separately qualified.

**Scroll and themes:** accepted calibration was retained. Current native wheel, continuous input, scene-theme transition/cancellation, and readable-surface tests pass. No physical wheel-detent or universal trackpad claim is made.

**Lifecycle and test workflow:** current suspension regression passes. The previous bounded transport scheduling repair is retained; its unresolved smooth-delivery finding is not erased. Preparation remains explicit and separate from dev/build/tests. The recording command now respects the same explicit `FILM_SOFTWARE_GPU=1` switch as browser qualification. No production renderer defaults were changed.

**Local Codex advice:** one fresh, bounded read-only run used `emil-design-eng`, relevant source files and one source frame. Its 445-word [diagnosis](../../../../../tools/open-design/.od/projects/experiment02-pointer-and-dark-background-diagnosis-20261012-41d5/diagnosis.md) supported preserving rounded-clock geometry and using background reconstruction rather than fragile full-arm segmentation. The actual report was inspected; generated advice was not treated as browser evidence or repository authority. Run `109fa053-2261-43da-94d3-b5a4ab1a4a14`.

## C. Test-execution efficiency

Follow-up executions, separate from historical checkpoint counts:

| Work | Observed result |
| --- | --- |
| Rounded-clock pre-fix regression | Expected failure; confirmed discarded geometry |
| Relevant motion/field unit tests | 16 passed, approximately 1.02s |
| Experiment typecheck | Passed |
| Initial GPU/compatibility pixel regressions | 2 passed, 5.6s; previous delivery identity |
| Preflight/delivery fixtures | 3 passed, approximately 350ms |
| Experiment unit subsystem | 58 passed across 10 files, 2.12s |
| Final six related Chromium browser cases | 6 passed, 47.7s; current publication |
| Native Chromium touch pan/cancellation | 1 passed, 4.8s; current publication |
| Built no-JS and reduced-motion/keyboard/self-hosting cases | 2 passed, 4.2s; current publication |
| Full functional G8 | 0; known delivery/performance qualification gaps remain |
| Extended performance and 1,800-step soak | 0; deliberately not repeated during this repair |
| Full media publication/integrity | One changed-identity run, 183.05s; no redundant preparation |
| Current GPU review recording | Completed in 38.802s; 16 named captures and 37.04s VP8 video |

Four small 14-frame pilot executions took 10.377, 9.769, 9.577 and 17.321 seconds (47.044s total). They used the same background-reconstruction approach with three evidence-driven boundary/noise/halo corrections. Only the selected continuous-matte treatment was published over the complete film. No further candidate or full-media loop is scheduled.

The first GPU review recording stopped immediately because its default browser chose Canvas2D (1.25s). Explicit software-GPU selection was then added to that diagnostic command; this is a tooling correction, not a hidden renderer downgrade. The final recording completed. Two builds were necessary: publication consumption, then refreshing the source receipt after the recording-script correction. Neither prepared media. Temporary syntax/preflight invocations initially used incorrect working directories; corrected AST and built preflight checks passed without generation. Final `git diff --check` passed.

Actual Chrome DevTools MCP was accessed through its installed official local wrapper and MCP SDK because those tools were absent from this session's directly exposed registry. Initial connection failures were fixed by passing the required user-session environment. One earlier navigation batch timed out and is unclassified. Page IDs proved session-local; re-listing pages corrected stale IDs. Sparse MCP hover calls were unsuitable for continuous-motion evidence because gaps reached seconds, so trusted CDP mouse movement was performed in the same browser. An initial unawaited controlled-step probe is not used as evidence; the awaited browser regression replaces it. These tooling failures were not converted into product passes.

## D. Visual evidence

Current final pixels and actual Chrome captures live in [followup/](../../test-results/scroll-film-navigator/finalization/followup/):

- `chrome-final-arm.png`: actual Chrome DevTools MCP screenshot, arm frame 145, new charcoal delivery.
- `chrome-final-native-clean.png` and `chrome-final-native-pigment.png`: matched native wall-clock gesture in the same Chrome browser; `chrome-final-native-pigment.json` records backend, state and field readback.
- `gpu-rounded.png` and `compatibility-rounded.png`: controlled equal-clock regression final pixels.
- [Both-film pigment/residue/decay pixels](../../test-results/scroll-film-navigator/finalization/navigator/) and `pixels-chromium.json`: regenerated by the six current browser cases.
- [Wheel capture](../../test-results/scroll-film-navigator/finalization/wheel-chromium.png) and corresponding JSON: regenerated current-publication wheel evidence.
- [Background comparisons](../../test-results/scroll-film-navigator/finalization/background/5aecc24eca3cdea5d000e8ddf97a48e426dea8b8153fae0ed075d2b0c42f82fa/): frames 49, 97 and 145, protected-detail masks and 12 consecutive pilot frames in a forward/reverse APNG.

Inspected directly: final Chrome arm and native pigment screenshots, current compatibility pigment, selected background comparison frames 49/97/145, and the current recording's six-image contact sheet (initial/slow/curve/contracting/reveal/tree). APNG generation and selected recording snapshots are not a claim that continuous playback was watched. The exact failing user profiles were not inspected.

The completed current short review recording lives at [review/navigator-hold.webm](../../test-results/scroll-film-navigator/finalization/review/navigator-hold.webm), with named captures, [contact sheet](../../test-results/scroll-film-navigator/finalization/review/navigator-contact.png), and [timeline](../../test-results/scroll-film-navigator/finalization/review/timeline.json). Video is 1200×800 VP8 at 25fps, 37.04s. Source fingerprint is `ec13dbadf9d84ecc1fcb688c98b83e8b7ab3da0aeb0339fbcb6c68e98b765ba7`; before/after fingerprints matched. Timeline shows release to CANCELING, re-press to GROWING, outward REVEALING, then one commit retained through a further 3.2-second press. The reveal screenshot already shows nearly full incoming coverage; it alone does not establish moving-rim quality. Older warm 1×/2×/rapid-reversal captures referenced in FINALIZATION D are historical: they are not new version-4 media evidence. Full current-source traversal, final artistic rim approval and complete physical-device review remain unavailable.

## E. Performance evidence

Hardware identifies `vmwgfx`, PCI `15AD:0405` (VMware virtual graphics), not a physical Intel/AMD integrated GPU. Actual Chrome 154 uses ANGLE/Vulkan SwiftShader at 1200×800, DPR 1. Physical display refresh and scanout are not measured. Native gesture field readback recorded mass 827.59, 2,622 occupied cells and two components; these are simulation observations, not FPS measurements.

The preceding mixed workload's 116.6ms median / 133.4ms p95 intervals and unacceptable warm traversal remain historical failures. No version-4 performance pass replaces them. No claim is made that background preprocessing fixes runtime pacing. The primary shader, fluid allocation and transport cache ownership were retained; new compatibility drawing has a bounded field and reused pixel allocation, but representative-device timing/endurance remain NOT VERIFIED.

The current Chrome initial-state estimate reports 16,464,896 renderer bytes. A full decoded 1600px cache remains bounded to 20 images / 115,200,000 pixel bytes. These are tracked allocation estimates; they exclude browser/driver overhead and are not measured total RAM/VRAM. Shared-memory hardware, mobile thermals and persistent memory trend require a suitable physical-device run using FINALIZATION E's preserved procedure.

## F. Qualification matrix

| Requirement | Status | Evidence / limit |
| --- | --- | --- |
| Experiment isolation / immutable canonical media / unchanged tree | PASS | Full validation and identical tree inventories |
| Equal-clock movement / visible navigator / decay | PASS | Current GPU and compatibility pixel tests; native Chrome gesture |
| Exact cause in user's Brave/Chrome profiles | NOT VERIFIED | Profile state was not reproduced; no invented causal claim |
| Organic marbling / merging / refraction / artistic acceptance | PARTIAL | GPU material retained; compatibility has no refraction; human acceptance absent |
| Wheel calibration / continuous native input | PASS | Current native browser case; physical devices NOT VERIFIED |
| Charcoal background delivery and protected-detail assertions | PASS | One complete publication; inspected pilot/live final images |
| Complete arm segmentation / recovered source quality / temporal acceptance | NOT VERIFIED | Contrast matte is intentionally limited; lossy encoding and low-contrast changes remain |
| Film-linked themes / suspension / native touch arbitration | PASS | Current targeted Chromium cases; no physical mobile claim |
| No-JS / reduced motion / keyboard / self-hosted built boundary | PASS | Two current built cases; full engine matrix not run |
| Warm 1×/2× smooth delivery | FAIL | Prior unresolved captured failure; current-media pass absent |
| Hold cancellation, reveal and single-press behavior | PARTIAL | Retained implementation and focused theme/cancellation coverage; broader qualification incomplete |
| Physical integrated-GPU/mobile pacing and endurance | NOT VERIFIED | Suitable hardware unavailable in this VM |
| Full G8 / production regression / current-source soak | NOT VERIFIED | Withheld rather than misrepresented as passing |
| Human artistic approval | NOT VERIFIED | User delegated implementation; final result not yet approved |

## G. Final verdict

**NEEDS_REWORK.** Navigator input/fallback defects and the dark-background delivery are repaired and have current browser evidence. The experiment still lacks acceptable demonstrated warm film continuity and physical integrated-GPU qualification. These are explicit remaining requirements, not silently skipped passes. No further speculative renderer/transport/media iterations are queued, and no production integration was attempted. The local preview uses the new publication at `http://127.0.0.1:4180/`.
