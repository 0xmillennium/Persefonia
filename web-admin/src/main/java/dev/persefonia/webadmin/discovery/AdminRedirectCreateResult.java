package dev.persefonia.webadmin.discovery;

import java.util.List;
import java.util.Objects;

public sealed interface AdminRedirectCreateResult
        permits AdminRedirectCreateResult.Created,
                AdminRedirectCreateResult.Noop,
                AdminRedirectCreateResult.Rejected {

    record Created() implements AdminRedirectCreateResult {
    }

    record Noop() implements AdminRedirectCreateResult {
    }

    record Rejected(
            List<AdminRedirectFieldError> fieldErrors,
            List<String> globalErrors) implements AdminRedirectCreateResult {
        public Rejected {
            fieldErrors = List.copyOf(Objects.requireNonNull(fieldErrors, "fieldErrors"));
            globalErrors = List.copyOf(Objects.requireNonNull(globalErrors, "globalErrors"));
        }
    }
}
