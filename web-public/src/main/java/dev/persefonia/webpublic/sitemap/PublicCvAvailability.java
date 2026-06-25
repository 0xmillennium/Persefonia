package dev.persefonia.webpublic.sitemap;

/**
 * Narrow, app-mediated check answering only whether a public Active CV page is available.
 *
 * <p>Sitemap generation must not read Media storage or source repositories from web-public, so the
 * concrete availability decision is delegated to an app-side adapter over the existing public Active
 * CV query.
 */
@FunctionalInterface
public interface PublicCvAvailability {
    boolean hasPublicCv();
}
