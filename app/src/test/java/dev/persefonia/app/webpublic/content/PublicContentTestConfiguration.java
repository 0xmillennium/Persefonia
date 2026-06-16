package dev.persefonia.app.webpublic.content;

import dev.persefonia.app.contentpublishing.application.InMemoryContentReadModelAdapter;
import dev.persefonia.contentpublishing.domain.model.series.port.SeriesRepository;
import dev.persefonia.contentpublishing.domain.translation.port.TranslationGroupRepository;
import dev.persefonia.webpublic.FrontendAssetResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-content-mvc-test")
public class PublicContentTestConfiguration {
    @Bean
    @Primary
    PublicContentTestRepository publicContentTestRepository() {
        return new PublicContentTestRepository();
    }

    @Bean
    @Primary
    InMemoryPublicRouteResolver publicContentTestRouteResolver() {
        return new InMemoryPublicRouteResolver();
    }

    @Bean
    @Primary
    PublicContentTestRevisionRepository publicContentTestRevisionRepository() {
        return new PublicContentTestRevisionRepository();
    }

    @Bean
    @Primary
    PublicContentTestTranslationGroupRepository publicContentTestTranslationGroupRepository() {
        return new PublicContentTestTranslationGroupRepository();
    }

    @Bean
    @Primary
    InMemoryContentReadModelAdapter publicContentTestReadModel(
            PublicContentTestRepository contentItems,
            ObjectProvider<SeriesRepository> seriesRepository,
            ObjectProvider<TranslationGroupRepository> translationGroups) {
        return new InMemoryContentReadModelAdapter(
                contentItems,
                seriesRepository.getIfAvailable(InMemoryContentReadModelAdapter::emptySeriesRepository),
                translationGroups.getIfAvailable(InMemoryContentReadModelAdapter::emptyTranslationGroupRepository));
    }

    @Bean
    @Primary
    FrontendAssetResolver publicContentTestAssetResolver() {
        return new FrontendAssetResolver() {
            @Override
            public String scriptPath(String entry) {
                if ("src/mermaid-loader.ts".equals(entry)) {
                    return "/assets/mermaid-loader-test.js";
                }
                return "/assets/main-test.js";
            }

            @Override
            public List<String> stylesheetPaths(String entry) {
                return List.of("/assets/main-test.css");
            }
        };
    }
}
