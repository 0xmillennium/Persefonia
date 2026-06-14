package dev.persefonia.discovery.application.projection;

import dev.persefonia.discovery.application.contract.SourceContext;
import dev.persefonia.discovery.application.contract.SourceEntityId;
import dev.persefonia.discovery.application.contract.SourceType;
import java.util.Objects;

public record RemoveDiscoverableResourceCommand(
        SourceContext sourceContext,
        SourceType sourceType,
        SourceEntityId sourceEntityId) {
    public RemoveDiscoverableResourceCommand {
        Objects.requireNonNull(sourceContext, "sourceContext");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
    }
}
