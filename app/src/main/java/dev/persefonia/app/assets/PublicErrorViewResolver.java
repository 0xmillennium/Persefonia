package dev.persefonia.app.assets;

import dev.persefonia.webpublic.FrontendAssetResolver;
import dev.persefonia.webpublic.content.PublicNotFoundPage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/** Render safe generic error text without reflecting request or exception details. */
@Component
public final class PublicErrorViewResolver implements ErrorViewResolver {
    private final FrontendAssetResolver assets;

    public PublicErrorViewResolver(FrontendAssetResolver assets) {
        this.assets = assets;
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        var page = new PublicNotFoundPage(
                status == HttpStatus.NOT_FOUND ? "Page not found" : "Page unavailable",
                status == HttpStatus.NOT_FOUND
                        ? "This page is unavailable. Explore the site using the navigation below."
                        : "Please try again later or return to the homepage.",
                true, assets.stylesheetPaths("src/main.ts"));
        return new ModelAndView("site/not-found", Map.of("page", page), status);
    }
}
