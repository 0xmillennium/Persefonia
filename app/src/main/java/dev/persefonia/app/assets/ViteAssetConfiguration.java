package dev.persefonia.app.assets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class ViteAssetConfiguration {
    @Bean
    ViteAssetResolver viteAssetResolver(ObjectMapper objectMapper) {
        return new ViteAssetResolver(
                objectMapper,
                new ClassPathResource(ViteAssetResolver.MANIFEST_CLASSPATH_LOCATION));
    }
}
