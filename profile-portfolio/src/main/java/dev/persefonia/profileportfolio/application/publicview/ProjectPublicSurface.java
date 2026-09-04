package dev.persefonia.profileportfolio.application.publicview;

import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import java.util.Map;
import java.util.Objects;

public record ProjectPublicSurface(
        Map<ContentLanguage, PublicUrl> directRoutes,
        boolean listed,
        boolean featured) {
    public ProjectPublicSurface {
        directRoutes = Map.copyOf(Objects.requireNonNull(directRoutes, "directRoutes"));
    }
}
