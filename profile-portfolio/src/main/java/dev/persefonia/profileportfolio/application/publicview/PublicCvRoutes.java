package dev.persefonia.profileportfolio.application.publicview;

import java.util.List;

public final class PublicCvRoutes {
    public static final String DEFAULT_PAGE = "/cv";
    public static final String DEFAULT_DOWNLOAD = "/cv/download";
    public static final String LANGUAGE_PAGE = "/cv/{language}";
    public static final String LANGUAGE_DOWNLOAD = "/cv/{language}/download";
    public static final List<String> ALL = List.of(
            DEFAULT_PAGE, DEFAULT_DOWNLOAD,
            "/cv/tr", "/cv/tr/download",
            "/cv/en", "/cv/en/download");

    private PublicCvRoutes() {
    }
}
