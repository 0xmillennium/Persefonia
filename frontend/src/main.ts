import './styles/main.css';
import { consumeNavigationMarker } from './navigation/marker';

document.documentElement.dataset.persefoniaAssets = 'ready';
const navigation = (
  performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
)?.type;
if (consumeNavigationMarker(location.href, navigation)) {
  void import('./navigation/cover')
    .then(({ restoreNavigationCover }) => restoreNavigationCover())
    .catch((error) => console.warn('Navigation presentation unavailable', error));
}
