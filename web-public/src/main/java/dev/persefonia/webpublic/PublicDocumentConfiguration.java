package dev.persefonia.webpublic;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration(proxyBeanMethods = false)
public class PublicDocumentConfiguration implements WebMvcConfigurer {
    private static final String REPRESENTATION_LOCALE = PublicDocumentConfiguration.class.getName() + ".locale";

    @Bean
    LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return request.getAttribute(REPRESENTATION_LOCALE) instanceof Locale locale
                        ? locale : super.resolveLocale(request);
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                request.removeAttribute(REPRESENTATION_LOCALE);
                return true;
            }

            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler, ModelAndView view) {
                if (view != null && view.getViewName() != null && view.getViewName().startsWith("site/")
                        && view.getModel().get("page") instanceof PublicDocument document) {
                    // DispatcherServlet applies the resolved locale immediately before rendering.
                    request.setAttribute(REPRESENTATION_LOCALE, Locale.forLanguageTag(document.htmlLanguage()));
                }
            }
        });
    }
}
