# Experiment 02 — hold reference refinement and qualification stop

This is the latest checkpoint, following [NAVIGATOR.md](NAVIGATOR.md). It covers the two newly supplied press/hold videos, the resulting hold/compositor changes, and the bounded qualification attempt. The experiment remains isolated from the production homepage. The overall assignment is incomplete.

## A. Repository state

Starting and final branch: `feat/scroll-film-navigator`; HEAD: `fa3aefe8b79a14751c47ddfb178783627a48fb3b`. Work remains uncommitted. No commit, push, dependency installation, production integration, or media preparation occurred during this pass. Existing changes were preserved.

This pass changed `src/hold.ts`, `src/hold-material.ts`, `src/material.ts`, `src/canvas-renderer.ts`, `src/main.ts`, `tests/hold.test.ts`, `tests/composition.spec.ts`, `tests/finalization.spec.ts`, `tests/support.ts`, `scripts/review-capture.mjs`, and the experiment reports/README. It added `src/hold-clock.ts`, `src/hold-optics.ts`, `tests/hold-clock.test.ts`, and this report. The complete inherited working-tree inventory is retained at `frontend/test-results/scroll-film-navigator/hold-refinement/working-tree-at-stop.txt`; that inventory precedes addition of this report and its qualification-document link.

Runtime/configuration/script fingerprint at stop: `3cb48f6b2c491a817f2e7da9a2862acf0e6ce1a42e530d305f6460772aac990b`. The built artifact's receipt contains the same fingerprint. Its latest command completion output was lost during context truncation, so receipt existence is reported separately from a claimed final build-command pass. Earlier builds and typechecks were observed passing. This fingerprint excludes tests and documentation.

The 384 canonical PNGs, the approved source film, the locked reference analysis, and supplied videos were not changed. Approved charcoal publication remains `1cc6d4a6616442c9557b9b434630f0b86ef76b2aeac91c86ccb6ff3502f79a0d`, source hash `b51a02afed8301ea4a1b6dede1669ba7c183175776c9499903ccc75bd7baf100`. Full integrity evidence belongs to [FOLLOWUP.md](FOLLOWUP.md); this pass did not repeat expensive validation.

## B. What was actually fixed

**Hold material.** The supplied clips show a broad translucent field with an irregular luminous boundary, strong local optical displacement, and continuity through interrupted holds. Their incoming-film visibility during growth conflicts with the explicit task contract: the implementation continues to tint the current film until full coverage and paired-frame readiness. Added a stored-distance growth rim and bounded, normalized refraction of up to 22 CSS pixels. The incoming film still opens outward from the captured center; the rim remains localized. The Canvas compatibility path now implements this optical boundary as well as tint, instead of a flat fill. Existing aperture ownership and single-press rules remain intact. GPU and Canvas red/blue film pixel-ownership scenarios both passed.

**Cancellation/resumption.** A pointer event could change hold phase before the next animation frame, assigning the preceding growth interval to cancellation. A separate monotonic hold clock now settles growth/cancellation before pointer and semantic events change state. Readiness and reveal remain owned by the render frame. Clock regressions passed; the unchanged native touch residue/pan scenario passed. A native mouse recording retained progress `0.3361693333311875` and material generation 1 on re-press, then committed exactly once despite another 3.2 seconds of pressure.

**Navigator rendering/decay.** The preceding material work in NAVIGATOR.md remains. WebKit sometimes retained the last green canvas image after the field had cleared and RAF had stopped. Explicit diagnostic readback showed the inactive uniform was zero and no GL error; another draw restored the film. A bounded final redraw now follows settlement. Its latch also arms when input arrives while an earlier idle redraw is pending. The final targeted WebKit both-film visible-pigment/decay case passed. Test-only reset/advance promises wait for the pending settled render; reduced-motion assertions wait for the actual media-query state, without weakening pixel or injection assertions.

**Compatibility cost.** Precomputed lattice gradients and shared interpolation weights remove redundant per-pixel sampling work. Hold mask ImageData and same-state material evaluation are cached. Output resolution, pigment concentration, refraction, and hold boundary remain intact. One actual Chrome native gesture reduced median JavaScript time from the preceding 40.6ms diagnostic to 20.6ms; this is software-environment evidence, not an iGPU pass.

**Scroll, media, themes, orchestration.** User-accepted wheel speed and charcoal arm background were retained. No new media transform or theme redesign was introduced. Existing separated preparation, functional, performance and soak commands remain. Review capture now explicitly selects GPU/compatibility and separate output labels. Historical warm 1×/2× delivery and software-GPU pacing failures have no replacement passing evidence.

**Local Codex advice.** One bounded `review-animations` advisory run inspected the contact sheets and scoped implementation; its actual report was read. Request `c2bbbe1c-5d4b-4680-9579-7200149b5e8b`, run `14631b5b-970c-44dc-8243-be7d80cedbb5`; artifact `/home/enes/tools/open-design/.od/projects/experiment02-navigator-fidelity-82cad9e2-22e4/hold-review.md`. Advice supplied hypotheses, not qualification or architectural authority. No generated framework or media was adopted.

## C. Test execution and stop

Observed useful checks, with repetitions distinguished:

| Check | Observed result / duration |
|---|---|
| Initial focused hold/clock units | 19 passed, 719ms; typecheck passed |
| After optical change | 19 passed, 745ms; typecheck passed |
| Hold/clock/pigment units | 22 passed, 2.20s |
| Experiment unit subsystem | 66 passed in 12 files, 6.49s; before final render-settlement changes |
| Native mouse cancellation/residue/retarget/single commit | Passed, 18.5s total |
| Native emulated-touch residue/pan | Passed, 4.4s total |
| GPU film composition | Passed, 17.3s total |
| Canvas film composition | Passed, 26.8s total |
| Latest targeted WebKit visible pigment/decay, both films | Passed, 8.1s total, after the activity-latch correction |
| Initial full functional attempt | 11 passed, 1 skipped, 1 failed, 173 not run; 1.2min |
| Second full functional attempt | 12 passed, 1 skipped, 1 failed, 172 not run; 1.3min |
| Remaining WebKit subset | 2 passed, 1 failed, 16 not run; 8.6s |
| Third full functional attempt | Startup page crash in first WebKit scenario (301ms); 185 not run |

Three full development-matrix attempts were launched with fail-fast, rather than three successful gates. The first two revealed observable stale pixels and asynchronous reduced-motion fixture state; the second also verified the corrected rounded-clock decay checks. Further narrow checks established that new activity arriving during a pending idle draw needed to re-arm settlement. Targeted diagnostics also included an unsuccessful two-RAF/flush approach, an initial black-baseline fixture failure, and an earlier WebKit startup page crash. The final targeted both-film check passed, then the third full attempt crashed before exercising the acceptance scenario. The unresolved startup crash was not repaired by changing launch flags or assertions.

The number of attempted full gates exceeded the intended single final gate because fixes were discovered during qualification. This is a process limitation, not a clean one-pass qualification. Individual focused retries were evidence-driven; failed assertions were retained. No built browser matrix or production frontend gate was completed for this final source. No new soak, heavy benchmark, transport recording, derivative preparation, or final-source full review recording was launched after the stop.

**Mandatory escalation:** WebKit page startup crashes remain unclassified. Expected: load the experiment and execute the gate. Observed: `page.goto: Page crashed`, before test behavior. Reproduction: the first scenario in the final fail-fast functional matrix; a preceding narrow retry also crashed on navigation. Classification: browser/environment/unknown; application causation has not been ruled out. The latest complete log is `hold-refinement/functional-closure.log` under the evidence root below. No OOM, GPU-driver or application cause is claimed. Repeated expensive-test failure under an unclassified environmental condition triggers task §4.3. Further tests and source modifications stopped; no qualification child process remained running when inspected. The existing preview and user browser processes were preserved. Next action is diagnosis of the browser crash in a stable supported runtime before another full gate, followed by representative physical-device qualification. No arbitrary timeout or test skip was added.

## D. Visual evidence and reference provenance

Evidence root: `frontend/test-results/scroll-film-navigator/`. These artifacts are ignored local files, not public delivery assets.

New reference clips in `frontend/experiments/sample-site-analysis/`:

- `navigator press hold effect.mp4`: 1874×1018, H.264, 30fps, 30.2333s; SHA-256 `ba3f23e57d2054d9b6e0fd58214ac22dfacecafdaf90a1e376c6b0901c85101d`.
- `special conditions at navigator hold press.mp4`: 1868×1014, H.264, 30fps, 22.4s; SHA-256 `cc3161af47f375e40f457579d290a081421d245193005350a82239b4b05d8410`.

Both 16-frame contact sheets and both 2fps temporal strips were generated and directly inspected: `hold-refinement/reference/{1-contact,2-contact,growth-temporal,interrupt-temporal}.png`. Full real-time reference playback was not claimed.

| Coverage | Actual artifact and inspection scope |
|---|---|
| Slow/fast navigator, curves, stopped decay, robot/tree contrast, wheel, growth/cancel, reveal, long press, reduced motion | `finalization/hold-refinement-{gpu,compatibility}/navigator-hold.webm`, 16 PNG marks and `timeline.json`; both nine-panel contacts were directly inspected. GPU timeline 43.822s / video 44.48s; compatibility 23.09s / video 24.04s. Fingerprint `71bec0b8bcb7478566d22f86046a09a8d06e1c1ca29fe2fa8abf9be6f4201587`. Intermediate captures before final WebKit settlement changes; not final-source evidence. |
| Actual residual re-press, commit and extended pressure | `hold-refinement/native-repress/{hold.webm,timeline.json,resumed.png,committed.png,extended-press.png}`; resumed screenshot directly inspected, native timeline verified. Fingerprint `7db136062a5d558ec0a552c5b37e049a12a14f44fbed83726bca77e3c0e90c52`. Intermediate; capture deliberately omits a screenshot during the time-sensitive cancellation mark. |
| Actual Chrome compatibility navigator and settlement | `hold-refinement/chrome-compatibility-native{.json,-painted.png,-post-stop.png}`; trusted CDP gestures in the Chrome DevTools browser, software runtime. Fingerprint `05ca14ec8a6fdb24d2c5577960138d041d091aec5e527329e71413a16cf62eb5`; intermediate. |
| WebKit stale-pixel diagnosis | `hold-refinement/webkit-decay/`; clean/immediate/redrawn images directly inspected. Before fix, inactive field persisted visually until another draw; not a final quality recording. |
| 1×/2× real captured film delivery and dark-frame comparisons | Historical artifacts linked in FINALIZATION.md/FOLLOWUP.md. No replacement current-source pass or new restoration comparison is claimed. |

The first broad GPU capture's screenshot latency consumed residue before its re-press mark. The separate native re-press recording establishes nonzero residual morphology; the broad GPU capture alone does not. Native input is distinguished from controlled simulation. No new Chrome MCP hold capture was completed after the final source freeze. No artistic PASS or final human approval is inferred from numeric assertions or contact sheets.

## E. Performance evidence

Host graphics are VMware `vmwgfx` (PCI `15AD:0405`); the observed GPU renderer is ANGLE/Vulkan SwiftShader. This is software graphics in a VM, not Intel/AMD shared-memory GPU qualification. Physical display refresh/scanout is unknown. Chrome DevTools browser: Chrome `154.0.8037.97`; recording browser: Chromium `143.0.7499.4`. Workloads used 1200×800 CSS pixels, DPR 1. Touch scenarios emulate devices; no physical mobile result is claimed.

For the intermediate Chrome compatibility native 40-segment figure-eight gesture, JavaScript median/p95 was 20.6/30.8ms; frame interval median/p95 was 33.2/33.4ms, p99 66.7ms. Previous same-environment diagnostics were 40.6/66.4ms JavaScript and 49.9/66.7ms intervals. Capture/IPC and software rendering confound these measurements; they show reduced CPU work, not smooth 60fps physical-device acceptance. The active-paint implementation remained unchanged by the final settlement correction, but this measurement is not a new final-fingerprint benchmark.

Reported compatibility renderer storage at that gesture was 27,256,608 bytes and CPU field storage 1,179,648 bytes. These are owned-array/raster accounting estimates, not total process RAM, resident GPU allocation or dedicated VRAM. Cache decoded-frame storage, browser internals and driver allocation are separate. The broad compatibility capture, including newly added hold optics, reported larger renderer storage and different timing; it is a different workload. No memory figure is represented as whole-system usage.

No visual feature was disabled or render resolution reduced for the CPU optimization. Historical software primary-GPU pacing and warm delivery failures remain open; final-source media presentation/decode readiness and representative iGPU/mobile budgets are not verified. The physical-device procedure in FINALIZATION.md remains required.

## F. Qualification matrix

PASS is scoped to the evidence named; it does not imply final full-matrix or artistic acceptance.

| Requirement | Status | Scope / open work |
|---|---|---|
| Navigator visibility | PASS | Browser pixels in preceding native captures; final targeted WebKit scenario |
| Organic morphology / artistic fidelity | PARTIAL | Transported material and inspected reference-informed captures; human acceptance pending |
| Decay / no stationary aura | PASS | Latest targeted WebKit both-film pixel test; other-engine final regression incomplete |
| Fine wheel | PASS | Retained user-accepted behavior; native capture advances one frame |
| Trackpad/touch | PARTIAL | Native emulated-touch regression passed; physical devices not verified |
| Scroll endpoints/reverse/history | PARTIAL | Existing unit/earlier evidence; final gate stopped at navigation |
| Warm 1×/2× visible delivery | FAIL | Historical captured failure unresolved; no replacement pass |
| Rapid-reversal delivery | PARTIAL | Existing scheduling corrections; current presentation qualification incomplete |
| Robot dark media / tree preservation | PASS | Approved retained publication; preceding full integrity evidence |
| Dark/light themes | PASS | Retained implementation and new intermediate transitions |
| Representative physical iGPU | NOT VERIFIED | Required hardware unavailable |
| Physical mobile performance | NOT VERIFIED | Emulation is not device profiling |
| Hold growth tints current film | PASS | GPU and Canvas pixel ownership checks |
| Cancellation / morphology / re-press | PASS | Clock regressions, unchanged native touch test and native residual recording |
| Outward reveal / local white rim | PASS | GPU/Canvas pixel checks and inspected intermediate contacts |
| At most one commit per physical press | PASS | Native mouse test and extended-pressure recording |
| Lifecycle / history / GPU recovery | PARTIAL | Existing fixes preserved; latest entire matrix incomplete |
| Keyboard / semantic / reduced / fallback | PARTIAL | Existing evidence, focused reduced-motion checks; final built matrix not run |
| Test separation / bounded debugging | PARTIAL | No repeated media/soak; three stopped full attempts; mandatory stop applied |
| Experiment isolation / immutable sources | PASS | Scoped working tree; no integration or media writes |
| Final functional and built gate | BLOCKED | Repeated unclassified WebKit page crash; gate incomplete |

## G. Final verdict

**BLOCKED** — qualification stopped under the explicit repeated-unclassified-browser-failure rule. Hold refinements and targeted fixes are retained. Required physical graphics/mobile evidence is unavailable, final-source recordings and full gates remain incomplete, and historical visible film-delivery/software-pacing failures remain unresolved. This verdict does not convert those failures into passes or declare the assignment complete. Diagnose the browser startup failure before resuming bounded qualification; obtain accelerated-device evidence before accepting runtime performance. No production integration is authorized by this report.
