import './styles/home/index.css';
import { enhanceHomepage } from './homepage/home-experience';

document.documentElement.dataset.persefoniaAssets = 'ready';

try {
  enhanceHomepage();
} catch (error) {
  // All content, navigation and native disclosures are already server rendered.
  console.error('Homepage enhancement could not initialize', error);
}
