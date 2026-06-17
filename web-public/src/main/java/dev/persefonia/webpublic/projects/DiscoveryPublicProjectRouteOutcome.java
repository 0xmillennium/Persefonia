package dev.persefonia.webpublic.projects;

import java.util.Objects;
import java.util.UUID;

public sealed interface DiscoveryPublicProjectRouteOutcome
        permits DiscoveryPublicProjectRouteOutcome.Project,
                DiscoveryPublicProjectRouteOutcome.Redirect,
                DiscoveryPublicProjectRouteOutcome.NotFound {
    record Project(
            UUID projectId,
            String language,
            String slug,
            String publicUrl,
            String canonicalUrl) implements DiscoveryPublicProjectRouteOutcome {
        public Project {
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(slug, "slug");
            Objects.requireNonNull(publicUrl, "publicUrl");
            Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        }
    }

    record Redirect(int statusCode, String targetPath) implements DiscoveryPublicProjectRouteOutcome {
        public Redirect {
            Objects.requireNonNull(targetPath, "targetPath");
        }
    }

    record NotFound() implements DiscoveryPublicProjectRouteOutcome {
    }
}
