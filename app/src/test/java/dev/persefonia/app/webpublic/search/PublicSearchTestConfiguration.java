package dev.persefonia.app.webpublic.search;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicSearchIndexQueryService;
import dev.persefonia.discovery.application.index.PublicSearchRequest;
import dev.persefonia.discovery.application.index.PublicSearchResult;
import dev.persefonia.discovery.application.index.PublicSearchResultPage;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class PublicSearchTestConfiguration {
    @Bean
    @Primary
    TrackingPublicSearchIndexQueryService publicSearchIndexQueryService() {
        return new TrackingPublicSearchIndexQueryService();
    }

    static final class TrackingPublicSearchIndexQueryService implements PublicSearchIndexQueryService {
        private int calls;
        private PublicSearchRequest lastRequest;
        private PublicSearchResultPage nextPage = new PublicSearchResultPage("portfolio", 20, 0, 0L, List.of());

        @Override
        public PublicSearchResultPage search(PublicSearchRequest request) {
            calls++;
            lastRequest = request;
            return nextPage;
        }

        void reset() {
            calls = 0;
            lastRequest = null;
            nextPage = new PublicSearchResultPage("portfolio", 20, 0, 0L, List.of());
        }

        int calls() {
            return calls;
        }

        PublicSearchRequest lastRequest() {
            return lastRequest;
        }

        void returnResults() {
            nextPage = new PublicSearchResultPage(
                    "portfolio",
                    20,
                    0,
                    1L,
                    List.of(new PublicSearchResult(
                            "PROJECT",
                            "project-id",
                            DiscoveryLanguage.EN,
                            "/en/projects/portfolio",
                            "https://0xmillennium.dev/en/projects/portfolio",
                            "Portfolio <Project>",
                            "A public <summary>",
                            Instant.parse("2026-06-20T12:00:00Z"),
                            Instant.parse("2026-06-21T12:00:00Z"),
                            1.0d)));
        }

        void returnEmptyPage() {
            nextPage = new PublicSearchResultPage("portfolio", 20, 0, 0L, List.of());
        }
    }
}
