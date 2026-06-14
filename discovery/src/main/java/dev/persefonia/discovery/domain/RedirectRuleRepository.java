package dev.persefonia.discovery.domain;

import dev.persefonia.discovery.application.contract.PublicUrl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RedirectRuleRepository {
    RedirectRule save(RedirectRule redirectRule);

    Optional<RedirectRule> findById(RedirectRuleId id);

    Optional<RedirectRule> findActiveBySourceUrl(PublicUrl sourceUrl);

    List<RedirectRule> findBySourceRef(SourceEntityRef sourceRef);

    Optional<RedirectRule> deactivate(RedirectRuleId id, Instant updatedAt);
}
