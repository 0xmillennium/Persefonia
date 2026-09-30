import "./styles/main.css";
import "./styles/home.css";
import { enhanceArchive } from "./archive";

document.documentElement.dataset.persefoniaAssets = "ready";

try {
  enhanceArchive();
} catch (error) {
  document.documentElement.classList.remove("archive-enhanced");
  console.error("Living Archive enhancement could not initialize", error);
}
