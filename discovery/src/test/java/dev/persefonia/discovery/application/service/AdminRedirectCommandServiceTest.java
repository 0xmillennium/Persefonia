package dev.persefonia.discovery.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.application.authorization.AdminRedirectCommandAuthorizationPolicy;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.port.CreateRedirectRulePort;
import dev.persefonia.discovery.application.port.DeactivateRedirectRulePort;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.CreateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleCommand;
import dev.persefonia.discovery.application.redirect.DeactivateRedirectRuleResult;
import dev.persefonia.discovery.application.redirect.RedirectRuleChangeSummary;
import dev.persefonia.discovery.application.redirect.RedirectRuleCreationResult;
import dev.persefonia.discovery.domain.RedirectRuleId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminRedirectCommandServiceTest {
    private static final AdminRedirectCommandActor OWNER =
            new AdminRedirectCommandActor(UUID.fromString("11111111-1111-1111-1111-111111111111"), true, true);
    private static final RedirectRuleId REDIRECT_ID =
            new RedirectRuleId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final RedirectRuleChangeSummary SUMMARY = new RedirectRuleChangeSummary(
            REDIRECT_ID,
            new PublicUrl("/old"),
            new PublicUrl("/new"),
            RedirectStatusCode.MOVED_PERMANENTLY_301,
            RedirectReason.MANUAL);

    @Test
    void activeOwnerCanCreateManualRedirectAndResultIsReturnedUnchanged() {
        RecordingPorts ports = new RecordingPorts();
        RedirectRuleCreationResult expected = new RedirectRuleCreationResult.Created(SUMMARY);
        ports.createResult = expected;

        RedirectRuleCreationResult result = service(ports).create(create(OWNER));

        assertThat(result).isSameAs(expected);
        assertThat(ports.createCalls).isEqualTo(1);
        assertThat(ports.lastCreate).satisfies(command -> {
            assertThat(command.reason()).isEqualTo(RedirectReason.MANUAL);
            assertThat(command.sourceContext()).isNull();
            assertThat(command.sourceType()).isNull();
            assertThat(command.sourceEntityId()).isNull();
        });
        assertThat(ports.authorization.lastCommandName).isEqualTo("discovery.redirect.create");
    }

    @Test
    void activeOwnerCanDeactivateAndResultIsReturnedUnchanged() {
        RecordingPorts ports = new RecordingPorts();
        DeactivateRedirectRuleResult expected = new DeactivateRedirectRuleResult.Deactivated(SUMMARY);
        ports.deactivateResult = expected;

        DeactivateRedirectRuleResult result = service(ports)
                .deactivate(new DeactivateManualRedirectCommand(OWNER, REDIRECT_ID));

        assertThat(result).isSameAs(expected);
        assertThat(ports.deactivateCalls).isEqualTo(1);
        assertThat(ports.lastDeactivate.redirectRuleId()).isEqualTo(REDIRECT_ID);
        assertThat(ports.authorization.lastCommandName).isEqualTo("discovery.redirect.deactivate");
    }

    @Test
    void nonOwnerIsRejectedBeforeCreatePort() {
        RecordingPorts ports = new RecordingPorts();
        AdminRedirectCommandActor editor = new AdminRedirectCommandActor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> service(ports).create(create(editor)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OWNER required");

        assertThat(ports.createCalls).isZero();
    }

    @Test
    void nonOwnerIsRejectedBeforeDeactivatePort() {
        RecordingPorts ports = new RecordingPorts();
        AdminRedirectCommandActor editor = new AdminRedirectCommandActor(UUID.randomUUID(), true, false);

        assertThatThrownBy(() -> service(ports).deactivate(new DeactivateManualRedirectCommand(editor, REDIRECT_ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OWNER required");

        assertThat(ports.deactivateCalls).isZero();
    }

    @Test
    void disabledOwnerIsRejectedBeforeEitherPort() {
        RecordingPorts ports = new RecordingPorts();
        AdminRedirectCommandActor disabledOwner = new AdminRedirectCommandActor(UUID.randomUUID(), false, true);

        assertThatThrownBy(() -> service(ports).create(create(disabledOwner)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(ports)
                        .deactivate(new DeactivateManualRedirectCommand(disabledOwner, REDIRECT_ID)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ports.createCalls).isZero();
        assertThat(ports.deactivateCalls).isZero();
    }

    @Test
    void unexpectedPortFailurePropagates() {
        RecordingPorts ports = new RecordingPorts();
        ports.createFailure = new IllegalStateException("persistence unavailable");

        assertThatThrownBy(() -> service(ports).create(create(OWNER)))
                .isSameAs(ports.createFailure);
    }

    @Test
    void nullCommandsAreRejectedBeforeAuthorization() {
        RecordingPorts ports = new RecordingPorts();

        assertThatThrownBy(() -> service(ports).create(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service(ports).deactivate(null)).isInstanceOf(NullPointerException.class);

        assertThat(ports.authorization.calls).isZero();
    }

    private static AdminRedirectCommandService service(RecordingPorts ports) {
        return new AdminRedirectCommandService(ports, ports, ports.authorization);
    }

    private static CreateManualRedirectCommand create(AdminRedirectCommandActor actor) {
        return new CreateManualRedirectCommand(
                actor,
                new PublicUrl("/old"),
                new PublicUrl("/new"),
                RedirectStatusCode.MOVED_PERMANENTLY_301);
    }

    private static final class RecordingPorts implements CreateRedirectRulePort, DeactivateRedirectRulePort {
        private final OwnerPolicy authorization = new OwnerPolicy();
        private RedirectRuleCreationResult createResult = new RedirectRuleCreationResult.Created(SUMMARY);
        private DeactivateRedirectRuleResult deactivateResult = new DeactivateRedirectRuleResult.Deactivated(SUMMARY);
        private RuntimeException createFailure;
        private CreateRedirectRuleCommand lastCreate;
        private DeactivateRedirectRuleCommand lastDeactivate;
        private int createCalls;
        private int deactivateCalls;

        @Override
        public RedirectRuleCreationResult create(CreateRedirectRuleCommand command) {
            createCalls++;
            lastCreate = command;
            if (createFailure != null) {
                throw createFailure;
            }
            return createResult;
        }

        @Override
        public DeactivateRedirectRuleResult deactivate(DeactivateRedirectRuleCommand command) {
            deactivateCalls++;
            lastDeactivate = command;
            return deactivateResult;
        }
    }

    private static final class OwnerPolicy implements AdminRedirectCommandAuthorizationPolicy {
        private int calls;
        private String lastCommandName;

        @Override
        public void requireOwner(AdminRedirectCommandActor actor, String commandName) {
            calls++;
            lastCommandName = commandName;
            if (!actor.active() || !actor.owner()) {
                throw new IllegalStateException("OWNER required");
            }
        }
    }
}
