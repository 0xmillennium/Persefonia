package dev.persefonia.profileportfolio.application.publicview;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ProjectPublicMutationFacts(
        UUID projectId,
        ProjectPublicExposureSnapshot beforeExposure,
        ProjectPublicExposureSnapshot afterExposure,
        Map<ContentLanguage, PublicUrl> beforeRoutes,
        Map<ContentLanguage, PublicUrl> afterRoutes) {
    public ProjectPublicMutationFacts {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(beforeExposure, "beforeExposure");
        Objects.requireNonNull(afterExposure, "afterExposure");
        beforeRoutes = Map.copyOf(Objects.requireNonNull(beforeRoutes, "beforeRoutes"));
        afterRoutes = Map.copyOf(Objects.requireNonNull(afterRoutes, "afterRoutes"));
    }
}
