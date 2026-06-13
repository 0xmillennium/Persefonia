package dev.persefonia.app.webpublic.content;

import dev.persefonia.webpublic.FrontendAssetResolver;
import java.util.List;
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
    PublicContentTestRevisionRepository publicContentTestRevisionRepository() {
        return new PublicContentTestRevisionRepository();
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
