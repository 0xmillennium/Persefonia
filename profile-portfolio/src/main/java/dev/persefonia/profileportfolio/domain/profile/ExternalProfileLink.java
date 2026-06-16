package dev.persefonia.profileportfolio.domain.profile;

import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import java.util.Objects;

public record ExternalProfileLink(
        ExternalProfileLinkId id,
        LinkLabel label,
        ExternalUrl url,
        SortOrder sortOrder) {
    public ExternalProfileLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(sortOrder, "sortOrder");
    }
}
