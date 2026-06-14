package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.Objects;

public record SourceEntityRef(
        SourceContext sourceContext,
        SourceType sourceType,
        SourceEntityId sourceEntityId) {
    public SourceEntityRef {
        Objects.requireNonNull(sourceContext, "sourceContext");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
    }
}
