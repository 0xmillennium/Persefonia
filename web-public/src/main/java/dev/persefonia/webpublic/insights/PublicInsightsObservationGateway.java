package dev.persefonia.webpublic.insights;

public interface PublicInsightsObservationGateway {
    void recordPageView(PublicInsightSurface surface);

    void recordSearchSubmitted();

    void recordCvViewed();

    void recordCvDownloaded();

    void recordContactSubmitted();

    void recordNotFound();
}
