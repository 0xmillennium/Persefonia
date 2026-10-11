# Experiment 02 remediation contract

The 2026-10-11 remediation assignment supersedes the baseline's incoming-film growth and repeating-hold semantics. This document records the implementation contract; it does not authorize production integration.

- Only the two immutable 192-frame 3840×2160 PNG sequences under `assets/frames/` are creative sources. Source order and endpoint correspondence are fixed.
- Native scroll progress selects `round(p × 191)`. No playback clock, autoplay, scroll easing or autonomous progression. Switching preserves progress.
- Desired, scheduled, decoded, uploaded and draw-submitted frames have separate ownership. Draw submission and physical screen presentation are different evidence.
- Prepare deterministic delivery assets offline; validate complete content and correspondence before atomically publishing a versioned manifest. Initial HTML exposes the first robotic-arm poster; incoming media starts only on eligible hold or explicit selection.
- Growing/canceling hold material tints the current film only. A persistent spatial field survives cancellation and re-press. Full translucent coverage precedes incoming readiness and outward reveal.
- Capture the effective press center when reveal begins. The incoming film expands outward from that point, leaving tinted outgoing film outside, with a soft white moving boundary. Both portions use matching scroll positions.
- Hold duration is approximately three seconds, reveal approximately 300–400 ms. One physical press commits at most once and then waits for release. Release during readiness wait retains earned completion; release during reveal allows settling. Incoming failure has a bounded, recoverable cancellation path.
- Primary full-quality rendering uses one WebGL2 context, GPU fluid advection/projection and bounded resources. Turquoise pigment, wakes, curling, residual islands and refraction require rendered qualification. Basic fallback is explicitly lower fidelity.
- Integrated graphics with shared RAM is a normal target. No discrete GPU or dedicated VRAM prerequisite is established; qualification must report the actual browser backend, combined decoded/graphics memory and ordinary frame costs. Software rendering is separate evidence from accelerated integrated graphics.
- Touch starts hold immediately; movement cancels incomplete hold and leaves native pan/zoom usable. Fine pointers keep trails and capture. Semantic controls, keyboard, visible focus and reduced-motion selection remain usable.
- Transient state is explicitly managed on resize, modality changes, visibility/history, capture loss, disposal and context loss. Durable film and native scroll survive restoration.
- Unit, pixel, browser, motion capture, media integrity and performance evidence are distinct. Physical GPU/Safari/mobile qualification cannot be inferred from automation or software rendering. Human material approval remains separate.

Execution gates: baseline → hold/reveal correction → measured media selection → transport/GPU implementation → liquid refinement → integrated/browser/lifecycle qualification → independent source/artifact review. Evidence and unresolved requirements are recorded in `QUALIFICATION.md`.
