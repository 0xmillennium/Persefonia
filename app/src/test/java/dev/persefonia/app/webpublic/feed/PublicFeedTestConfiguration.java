package dev.persefonia.app.webpublic.feed;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicFeedEntry;
import dev.persefonia.discovery.application.index.PublicFeedIndexQueryService;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-feed-mvc-test")
class PublicFeedTestConfiguration {
    @Bean
    @Primary
    StubPublicFeedIndexQueryService publicFeedIndexQueryService() {
        return new StubPublicFeedIndexQueryService();
    }

    static final class StubPublicFeedIndexQueryService implements PublicFeedIndexQueryService {
        private static final List<PublicFeedEntry> DEFAULT_ENTRIES = List.of(
                entry("ARTICLE", "/en/articles/published-article", "Published Article",
                        "An eligible article summary",
                        Instant.parse("2026-06-20T08:00:00Z"), Instant.parse("2026-06-24T12:34:56Z")),
                entry("NOTE", "/tr/notes/published-note", "Yayinlanan Not",
                        "Uygun bir not ozeti",
                        Instant.parse("2026-06-18T08:00:00Z"), Instant.parse("2026-06-19T09:00:00Z")),
                entry("RESEARCH", "/en/research/published-research", "Published Research",
                        "An eligible research summary",
                        Instant.parse("2026-06-10T08:00:00Z"), Instant.parse("2026-06-11T09:00:00Z")));

        private List<PublicFeedEntry> entries = DEFAULT_ENTRIES;

        @Override
        public List<PublicFeedEntry> findLatestFeedEntries(int limit) {
            return entries;
        }

        void entries(List<PublicFeedEntry> entries) {
            this.entries = entries;
        }

        void reset() {
            this.entries = DEFAULT_ENTRIES;
        }

        static PublicFeedEntry entry(
                String sourceType,
                String publicUrl,
                String title,
                String summary,
                Instant publishedAt,
                Instant updatedAt) {
            return new PublicFeedEntry(
                    sourceType,
                    "id-" + Math.abs(publicUrl.hashCode()),
                    DiscoveryLanguage.EN,
                    publicUrl,
                    "https://0xmillennium.dev" + publicUrl,
                    title,
                    summary,
                    publishedAt,
                    updatedAt);
        }
    }
}
