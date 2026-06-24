package dev.persefonia.discovery.application.index;

import java.util.List;

public interface PublicSitemapIndexQueryService {
    List<PublicSitemapEntry> findSitemapEntries(int limit);
}
