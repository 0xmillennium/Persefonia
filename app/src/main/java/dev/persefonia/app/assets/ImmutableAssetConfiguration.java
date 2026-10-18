package dev.persefonia.app.assets;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Only build-qualified immutable URLs may bypass the default cache policy. */
@Configuration(proxyBeanMethods = false)
class ImmutableAssetConfiguration implements WebMvcConfigurer {
    private final Set<String> paths;

    ImmutableAssetConfiguration(ObjectMapper mapper) {
        var resource = new ClassPathResource("static/assets/.vite/immutable-assets.json");
        try (var input = resource.getInputStream()) {
            paths = Set.copyOf(mapper.readValue(input, new TypeReference<java.util.List<String>>() {}));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read qualified immutable asset inventory", exception);
        }
        for (String path : paths) {
            if (!path.startsWith("/assets/") || path.contains("..") || path.contains("%")
                    || path.contains("\\") || path.contains("*") || path.contains("?")
                    || path.contains("{") || path.contains("}")
                    || !new ClassPathResource("static" + path).exists()) {
                throw new IllegalStateException("Invalid qualified immutable asset path: " + path);
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (paths.isEmpty()) return;
        String cacheControl = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable().getHeaderValue();
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (handler instanceof ResourceHttpRequestHandler && paths.contains(path)
                        && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
                    response.setHeader("Cache-Control", cacheControl);
                }
                return true;
            }
        }).addPathPatterns("/assets/**");
    }
}
