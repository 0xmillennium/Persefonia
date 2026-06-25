package dev.persefonia.webpublic.sitemap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Explicit, bounded list of static public routes eligible for the sitemap.
 *
 * <p>Only stable public navigation pages belong here. The CV page is conditional on an active CV
 * existing. Search, CV downloads, media binaries, feed/sitemap/robots, tag and series pages, and all
 * admin/OAuth/preview/actuator routes are never included.
 */
@Component
public final class PublicSitemapStaticRouteProvider {
    static final String HOME = "/";
    static final List<String> PROJECT_LISTINGS = List.of("/tr/projects", "/en/projects");
    static final String CV = "/cv";

    private final PublicCvAvailability cvAvailability;

    public PublicSitemapStaticRouteProvider(PublicCvAvailability cvAvailability) {
        this.cvAvailability = Objects.requireNonNull(cvAvailability, "cvAvailability");
    }

    /**
     * Static public paths, in deterministic order, with no synthetic {@code lastmod}.
     */
    public List<String> staticPaths() {
        List<String> paths = new ArrayList<>();
        paths.add(HOME);
        paths.addAll(PROJECT_LISTINGS);
        if (cvAvailability.hasPublicCv()) {
            paths.add(CV);
        }
        return List.copyOf(paths);
    }
}
