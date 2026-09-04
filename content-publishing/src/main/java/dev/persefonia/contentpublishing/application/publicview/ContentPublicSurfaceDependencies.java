package dev.persefonia.contentpublishing.application.publicview;

import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.List;
import java.util.Objects;

public record ContentPublicSurfaceDependencies(
        List<PublicUrl> tagRoutes,
        List<PublicUrl> activeSeriesRoutes,
        List<PublicUrl> publicTranslationMemberRoutes,
        boolean overflow) {
    public ContentPublicSurfaceDependencies {
        tagRoutes = List.copyOf(Objects.requireNonNull(tagRoutes, "tagRoutes"));
        activeSeriesRoutes = List.copyOf(Objects.requireNonNull(activeSeriesRoutes, "activeSeriesRoutes"));
        publicTranslationMemberRoutes = List.copyOf(
                Objects.requireNonNull(publicTranslationMemberRoutes, "publicTranslationMemberRoutes"));
    }
}
