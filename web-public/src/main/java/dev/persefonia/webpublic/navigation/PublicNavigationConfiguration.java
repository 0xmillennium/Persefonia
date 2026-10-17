package dev.persefonia.webpublic.navigation;

import dev.persefonia.webpublic.PublicHomeViewModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class PublicNavigationConfiguration implements WebMvcConfigurer {
    private final PublicNavigationLinks links;

    public PublicNavigationConfiguration(PublicNavigationLinks links) {
        this.links = links;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler, ModelAndView view) {
                if (view == null || view.getViewName() == null || !view.getViewName().startsWith("site/")) return;
                String language = "en";
                if (view.getModel().get("page") instanceof PublicHomeViewModel home) {
                    language = home.defaultLanguage().toLowerCase(Locale.ROOT);
                } else if (request.getRequestURI().matches("^/(?:cv/)?tr(?:/.*)?$")) {
                    language = "tr";
                }
                view.addObject("navigationLinks", links.forLanguage(language));
            }
        });
    }
}
