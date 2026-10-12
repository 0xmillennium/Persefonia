import { IdentityAnimation } from "./animation";
import { IdentityController } from "./controller";

const stage = document.querySelector<HTMLElement>("#stage")!;
const trigger = document.querySelector<HTMLButtonElement>("#identity")!;
if (typeof Element.prototype.animate === "function") {
  const controller = new IdentityController(stage, trigger,
    new IdentityAnimation(trigger.querySelector<SVGSVGElement>("svg")!));
  window.addEventListener("pagehide", () => controller.destroy());
  // pagehide disposes the lifetime; a BFCache restore starts a fresh one.
  window.addEventListener("pageshow", event => { if (event.persisted) window.location.reload(); });
} else {
  document.querySelector<HTMLElement>("#instruction")!.textContent = "Animation unavailable in this browser. The original identity is shown.";
}
