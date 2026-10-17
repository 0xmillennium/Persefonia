package dev.persefonia.discovery.application.index;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import java.util.List;

/** Listed, published content that may be promoted on the public homepage. */
public interface PublicHomepageContentQueryService {
    List<PublicHomepageContentEntry> latestWriting(DiscoveryLanguage language, int limit);

    List<PublicHomepageContentEntry> latestArticles(DiscoveryLanguage language, int limit);

    List<PublicHomepageContentEntry> latestNotes(DiscoveryLanguage language, int limit);

    List<PublicHomepageContentEntry> latestResearch(DiscoveryLanguage language, int limit);
}
