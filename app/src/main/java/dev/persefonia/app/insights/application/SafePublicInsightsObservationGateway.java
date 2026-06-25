package dev.persefonia.app.insights.application;

import dev.persefonia.insights.application.service.RecordInsightObservationCommandService;
import dev.persefonia.insights.domain.model.InsightMetric;
import dev.persefonia.insights.domain.model.InsightObservation;
import dev.persefonia.insights.domain.model.InsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightSurface;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SafePublicInsightsObservationGateway implements PublicInsightsObservationGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(SafePublicInsightsObservationGateway.class);

    private final RecordInsightObservationCommandService observations;

    public SafePublicInsightsObservationGateway(RecordInsightObservationCommandService observations) {
        this.observations = Objects.requireNonNull(observations, "observations must not be null");
    }

    @Override
    public void recordPageView(PublicInsightSurface surface) {
        Objects.requireNonNull(surface, "surface must not be null");
        record(InsightMetric.PUBLIC_PAGE_VIEW, map(surface));
    }

    @Override
    public void recordSearchSubmitted() {
        record(InsightMetric.PUBLIC_SEARCH_SUBMITTED, InsightSurface.SEARCH);
    }

    @Override
    public void recordCvViewed() {
        record(InsightMetric.PUBLIC_CV_VIEWED, InsightSurface.CV);
    }

    @Override
    public void recordCvDownloaded() {
        record(InsightMetric.PUBLIC_CV_DOWNLOADED, InsightSurface.CV);
    }

    @Override
    public void recordContactSubmitted() {
        record(InsightMetric.PUBLIC_CONTACT_SUBMITTED, InsightSurface.CONTACT);
    }

    @Override
    public void recordNotFound() {
        record(InsightMetric.PUBLIC_NOT_FOUND, InsightSurface.NOT_FOUND);
    }

    private void record(InsightMetric metric, InsightSurface surface) {
        try {
            observations.record(InsightObservation.one(metric, surface));
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to record aggregate insight counter.");
        }
    }

    private static InsightSurface map(PublicInsightSurface surface) {
        return switch (surface) {
            case HOME -> InsightSurface.HOME;
            case CONTENT_DETAIL -> InsightSurface.CONTENT_DETAIL;
            case PROJECT_INDEX -> InsightSurface.PROJECT_INDEX;
            case PROJECT_DETAIL -> InsightSurface.PROJECT_DETAIL;
            case TAG_INDEX -> InsightSurface.TAG_INDEX;
            case SERIES_INDEX -> InsightSurface.SERIES_INDEX;
            case CONTACT -> InsightSurface.CONTACT;
            case CV -> InsightSurface.CV;
            case SEARCH -> InsightSurface.SEARCH;
            case NOT_FOUND -> InsightSurface.NOT_FOUND;
        };
    }
}
