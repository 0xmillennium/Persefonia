package dev.persefonia.app.webpublic.sitemap;

import dev.persefonia.discovery.application.contract.DiscoveryLanguage;
import dev.persefonia.discovery.application.index.PublicSitemapEntry;
import dev.persefonia.discovery.application.index.PublicSitemapIndexQueryService;
import dev.persefonia.webpublic.sitemap.PublicCvAvailability;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-sitemap-mvc-test")
class PublicSitemapTestConfiguration {
    @Bean
    @Primary
    StubPublicSitemapIndexQueryService publicSitemapIndexQueryService() {
        return new StubPublicSitemapIndexQueryService();
    }

    @Bean
    @Primary
    ToggleablePublicCvAvailability publicCvAvailability() {
        return new ToggleablePublicCvAvailability();
    }

    static final class StubPublicSitemapIndexQueryService implements PublicSitemapIndexQueryService {
        private static final List<PublicSitemapEntry> DEFAULT_ENTRIES = List.of(
                new PublicSitemapEntry(
                        "/en/projects/portfolio",
                        "https://0xmillennium.dev/en/projects/portfolio",
                        DiscoveryLanguage.EN,
                        Instant.parse("2026-06-24T12:00:00Z")),
                new PublicSitemapEntry(
                        "/en/articles/hello",
                        "https://0xmillennium.dev/en/articles/hello",
                        DiscoveryLanguage.EN,
                        Instant.parse("2026-06-20T08:30:00Z")));

        private List<PublicSitemapEntry> entries = DEFAULT_ENTRIES;

        @Override
        public List<PublicSitemapEntry> findSitemapEntries(int limit) {
            return entries;
        }

        void entries(List<PublicSitemapEntry> entries) {
            this.entries = entries;
        }

        void reset() {
            this.entries = DEFAULT_ENTRIES;
        }
    }

    static final class ToggleablePublicCvAvailability implements PublicCvAvailability {
        private boolean present = true;

        @Override
        public boolean hasPublicCv() {
            return present;
        }

        void present(boolean present) {
            this.present = present;
        }
    }
}
