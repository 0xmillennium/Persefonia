package dev.persefonia.discovery.domain;

import java.util.Objects;
import java.util.UUID;

public record RedirectRuleId(UUID value) {
    public RedirectRuleId {
        Objects.requireNonNull(value, "value");
    }

    public static RedirectRuleId random() {
        return new RedirectRuleId(UUID.randomUUID());
    }
}
