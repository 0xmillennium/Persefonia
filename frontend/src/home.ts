import "./styles/main.css";
import "./styles/home.css";
import { enhanceArchive } from "./archive";
import { bootstrapHomepageRuntime } from "./homepage/runtime/bootstrap";

document.documentElement.dataset.persefoniaAssets = "ready";

try {
  enhanceArchive();
} catch (error) {
  document.documentElement.classList.remove("archive-enhanced");
  console.error("Living Archive enhancement could not initialize", error);
}

void bootstrapHomepageRuntime(document.querySelector<HTMLElement>("[data-homepage-runtime]"))
  .then(dispose => {
    if (dispose) window.addEventListener("pagehide", dispose, { once: true });
  });
