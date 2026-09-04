package dev.persefonia.contentpublishing.application.publicview;

public record ContentPublicExposureSnapshot(
        boolean directReachable,
        boolean listed,
        boolean sitemapEligible,
        boolean feedEligible) {

    public static ContentPublicExposureSnapshot none() {
        return new ContentPublicExposureSnapshot(false, false, false, false);
    }
}
