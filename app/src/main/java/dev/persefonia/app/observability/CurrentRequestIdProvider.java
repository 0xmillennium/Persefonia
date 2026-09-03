package dev.persefonia.app.observability;

import dev.persefonia.audit.domain.record.RequestId;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public final class CurrentRequestIdProvider {
    public Optional<String> currentRequestId() {
        return Optional.ofNullable(MDC.get(RequestIdFilter.MDC_KEY))
                .map(RequestId::of)
                .map(RequestId::value);
    }
}
