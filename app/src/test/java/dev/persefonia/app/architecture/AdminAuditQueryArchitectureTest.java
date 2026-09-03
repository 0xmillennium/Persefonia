package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.app.audit.query.JdbcAuditRecordReadAdapter;
import dev.persefonia.audit.application.port.AuditQueryPort;
import dev.persefonia.audit.application.port.AuditRecordReadPort;
import dev.persefonia.audit.application.service.AuditQueryService;
import dev.persefonia.audit.domain.record.port.AuditRecordRepository;
import dev.persefonia.webadmin.audit.AdminAuditController;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdminAuditQueryArchitectureTest {
    @Test
    void controllerDependsOnApplicationQueryBoundaryOnly() {
        Set<Class<?>> constructorTypes = Arrays.stream(AdminAuditController.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
                .collect(Collectors.toSet());
        assertThat(constructorTypes).contains(AuditQueryPort.class);
        assertThat(constructorTypes).doesNotContain(
                AuditRecordRepository.class,
                AuditRecordReadPort.class,
                JdbcAuditRecordReadAdapter.class,
                org.springframework.jdbc.core.JdbcTemplate.class,
                org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class);
    }

    @Test
    void queryServiceUsesReadPortRatherThanAggregateRepository() {
        assertThat(AuditQueryService.class.getDeclaredConstructors()).singleElement().satisfies(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(AuditRecordReadPort.class));
        assertThat(Arrays.stream(AuditQueryService.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .doesNotContain(AuditRecordRepository.class.getName());
    }

    @Test
    void aggregateRepositoryRemainsAggregateOrientedAndHasNoChildRepositories() {
        assertThat(Arrays.stream(AuditRecordRepository.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("append", "findById", "findRecent")
                .doesNotContain("search", "findByAction", "findByActor", "findByEntity");
        assertThatThrownBy(() -> Class.forName("dev.persefonia.audit.domain.record.port.AuditChangeRepository"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("dev.persefonia.audit.domain.record.port.AuditMetadataRepository"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
