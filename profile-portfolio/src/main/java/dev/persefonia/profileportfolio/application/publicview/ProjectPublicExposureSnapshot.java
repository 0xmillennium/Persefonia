package dev.persefonia.profileportfolio.application.publicview;

public record ProjectPublicExposureSnapshot(
        boolean directReachable,
        boolean listed,
        boolean sitemapEligible,
        boolean homepageFeaturedEligible) {
}
