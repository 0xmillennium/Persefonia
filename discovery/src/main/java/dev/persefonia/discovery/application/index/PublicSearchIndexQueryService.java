package dev.persefonia.discovery.application.index;

public interface PublicSearchIndexQueryService {
    PublicSearchResultPage search(PublicSearchRequest request);
}
