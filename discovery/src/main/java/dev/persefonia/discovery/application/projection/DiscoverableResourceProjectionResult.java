package dev.persefonia.discovery.application.projection;

import java.util.Objects;

public sealed interface DiscoverableResourceProjectionResult
        permits DiscoverableResourceProjectionResult.Updated,
                DiscoverableResourceProjectionResult.Removed,
                DiscoverableResourceProjectionResult.Noop,
                DiscoverableResourceProjectionResult.Rejected {

    record Updated() implements DiscoverableResourceProjectionResult {
    }

    record Removed() implements DiscoverableResourceProjectionResult {
    }

    record Noop() implements DiscoverableResourceProjectionResult {
    }

    record Rejected(Reason reason) implements DiscoverableResourceProjectionResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        INVALID_INPUT,
        CONFLICT,
        UNSUPPORTED_RESOURCE
    }
}
