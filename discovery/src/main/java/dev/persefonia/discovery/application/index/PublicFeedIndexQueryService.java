package dev.persefonia.discovery.application.index;

import java.util.List;

public interface PublicFeedIndexQueryService {
    List<PublicFeedEntry> findLatestFeedEntries(int limit);
}
