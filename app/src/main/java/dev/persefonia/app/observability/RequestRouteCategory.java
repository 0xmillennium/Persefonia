package dev.persefonia.app.observability;

/**
 * Bounded classification of a request used as an operational log field in place
 * of the raw request path. Categories are derived from the request path prefix
 * and final status only; no raw path, query string, or path variable is ever
 * retained or logged.
 */
enum RequestRouteCategory {
    PUBLIC_HOME,
    PUBLIC_CONTENT,
    PUBLIC_PROJECT,
    PUBLIC_SEARCH,
    PUBLIC_CONTACT,
    PUBLIC_CV,
    PUBLIC_NOT_FOUND,
    ADMIN,
    STATIC_ASSET,
    MEDIA_VARIANT,
    SITEMAP,
    ROBOTS,
    FEED,
    OAUTH,
    ACTUATOR,
    OTHER;

    static RequestRouteCategory classify(String path, int status) {
        RequestRouteCategory category = byPath(path);
        if (category == OTHER && status == 404) {
            return PUBLIC_NOT_FOUND;
        }
        return category;
    }

    private static RequestRouteCategory byPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return PUBLIC_HOME;
        }
        if (path.startsWith("/actuator")) {
            return ACTUATOR;
        }
        if (path.startsWith("/admin")) {
            return ADMIN;
        }
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) {
            return OAUTH;
        }
        if (path.startsWith("/assets/")) {
            return STATIC_ASSET;
        }
        if (path.startsWith("/media/assets/")) {
            return MEDIA_VARIANT;
        }
        if (path.equals("/sitemap.xml")) {
            return SITEMAP;
        }
        if (path.equals("/robots.txt")) {
            return ROBOTS;
        }
        if (path.equals("/feed.xml")) {
            return FEED;
        }
        if (path.equals("/search")) {
            return PUBLIC_SEARCH;
        }
        if (path.equals("/contact")) {
            return PUBLIC_CONTACT;
        }
        if (path.equals("/cv") || path.startsWith("/cv/")) {
            return PUBLIC_CV;
        }
        if (isLocalizedProjectPath(path)) {
            return PUBLIC_PROJECT;
        }
        if (isLocalizedPublicPath(path)) {
            return PUBLIC_CONTENT;
        }
        return OTHER;
    }

    private static boolean isLocalizedProjectPath(String path) {
        return path.equals("/tr/projects")
                || path.equals("/en/projects")
                || path.startsWith("/tr/projects/")
                || path.startsWith("/en/projects/");
    }

    private static boolean isLocalizedPublicPath(String path) {
        return path.startsWith("/tr/") || path.startsWith("/en/");
    }
}
