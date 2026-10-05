import "./styles/home.css";
import { enhanceHomepage } from "./home-experience";

document.documentElement.dataset.persefoniaAssets = "ready";

try {
  enhanceHomepage();
} catch (error) {
  // All content, navigation and native disclosures are already server rendered.
  console.error("Homepage enhancement could not initialize", error);
}
