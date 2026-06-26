package dev.persefonia.app.audit;

import dev.persefonia.audit.application.port.AppendAuditRecordPort;
import dev.persefonia.audit.application.port.AuditQueryPort;
import dev.persefonia.audit.application.service.AuditAppendService;
import dev.persefonia.audit.application.service.AuditQueryService;
import dev.persefonia.audit.application.service.AuditRecordFactory;
import dev.persefonia.audit.application.service.AuditSafeValuePolicy;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free audit application services. The repository contract is
 * supplied by the JDBC adapter under {@code dev.persefonia.app.audit.persistence}.
 * Audit is intentionally not wired into any source command service here.
 */
@Configuration(proxyBeanMethods = false)
class AuditApplicationConfiguration {
    @Bean
    AuditSafeValuePolicy auditSafeValuePolicy() {
        return new AuditSafeValuePolicy();
    }

    @Bean
    AuditRecordFactory auditRecordFactory(AuditSafeValuePolicy policy, Clock clock) {
        return new AuditRecordFactory(policy, clock);
    }

    @Bean
    AppendAuditRecordPort appendAuditRecordPort(AuditRecordFactory factory, AuditRecordRepository repository) {
        return new AuditAppendService(factory, repository);
    }

    @Bean
    AuditQueryPort auditQueryPort(AuditRecordRepository repository) {
        return new AuditQueryService(repository);
    }
}
