package dev.persefonia.app.audit;

import dev.persefonia.audit.domain.record.AuditRecord;
import dev.persefonia.audit.domain.record.AuditRecordId;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Supplies durable-port semantics to datasource-free MVC test contexts only. */
@TestConfiguration(proxyBeanMethods = false)
public class MvcAuditTestConfiguration {
    @Bean
    @Primary
    AuditRecordRepository mvcAuditRecordRepository() {
        return new InMemoryAuditRecordRepository();
    }

    private static final class InMemoryAuditRecordRepository implements AuditRecordRepository {
        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public synchronized void append(AuditRecord record) {
            records.add(record);
        }

        @Override
        public synchronized Optional<AuditRecord> findById(AuditRecordId id) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }

        @Override
        public synchronized List<AuditRecord> findRecent(int limit) {
            int fromIndex = Math.max(records.size() - Math.max(limit, 0), 0);
            List<AuditRecord> recent = new ArrayList<>(records.subList(fromIndex, records.size()));
            java.util.Collections.reverse(recent);
            return List.copyOf(recent);
        }
    }
}
