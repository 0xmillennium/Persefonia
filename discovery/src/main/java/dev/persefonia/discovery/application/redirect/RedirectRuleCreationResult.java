package dev.persefonia.discovery.application.redirect;

import java.util.Objects;

public sealed interface RedirectRuleCreationResult
        permits RedirectRuleCreationResult.Created,
                RedirectRuleCreationResult.Noop,
                RedirectRuleCreationResult.Rejected {

    record Created(RedirectRuleChangeSummary redirect) implements RedirectRuleCreationResult {
        public Created {
            Objects.requireNonNull(redirect, "redirect");
        }
    }

    record Noop(RedirectRuleChangeSummary existingRedirect) implements RedirectRuleCreationResult {
        public Noop {
            Objects.requireNonNull(existingRedirect, "existingRedirect");
        }
    }

    record Rejected(Reason reason) implements RedirectRuleCreationResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        INVALID_INPUT,
        DUPLICATE_ACTIVE_SOURCE,
        LOOP_DETECTED,
        UNSUPPORTED_STATUS
    }
}
