package dev.persefonia.profileportfolio.domain.project;

import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import java.util.Objects;

public record ProjectLink(
        ProjectLinkId id,
        LinkLabel label,
        ExternalUrl url,
        ProjectLinkType linkType,
        SortOrder sortOrder) {
    public ProjectLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(linkType, "linkType");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
