package dev.persefonia.audit.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditActorAndEntityInvariantsTest {
    private static final SourceContext CONTEXT = SourceContext.of("iam");
    private static final SourceType SOURCE_TYPE = SourceType.of("admin_account");
    private static final SourceEntityId ACTOR_ID = SourceEntityId.from(UUID.randomUUID());
    private static final DisplayName DISPLAY = DisplayName.of("Jane Admin");

    @Test
    void validAdminActorIsAccepted() {
        AuditActorRef actor = AuditActorRef.admin(CONTEXT, SOURCE_TYPE, ACTOR_ID, DISPLAY);

        assertThat(actor.type()).isEqualTo(AuditActorType.ADMIN);
        assertThat(actor.context()).contains(CONTEXT);
        assertThat(actor.sourceType()).contains(SOURCE_TYPE);
        assertThat(actor.id()).contains(ACTOR_ID);
        assertThat(actor.display()).isEqualTo(DISPLAY);
    }

    @Test
    void adminActorWithoutContextIsRejected() {
        assertThatThrownBy(() -> AuditActorRef.admin(null, SOURCE_TYPE, ACTOR_ID, DISPLAY))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("context");
    }

    @Test
    void adminActorWithoutSourceTypeIsRejected() {
        assertThatThrownBy(() -> AuditActorRef.admin(CONTEXT, null, ACTOR_ID, DISPLAY))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("source type");
    }

    @Test
    void adminActorWithoutIdIsRejected() {
        assertThatThrownBy(() -> AuditActorRef.admin(CONTEXT, SOURCE_TYPE, null, DISPLAY))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("id");
    }

    @Test
    void adminActorWithoutDisplayIsRejected() {
        assertThatThrownBy(() -> AuditActorRef.admin(CONTEXT, SOURCE_TYPE, ACTOR_ID, null))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("display");
    }

    @Test
    void validSystemActorIsAccepted() {
        AuditActorRef actor = AuditActorRef.system(DisplayName.of("System"));

        assertThat(actor.type()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(actor.context()).isEmpty();
        assertThat(actor.sourceType()).isEmpty();
        assertThat(actor.id()).isEmpty();
    }

    @Test
    void systemActorWithoutDisplayIsRejected() {
        assertThatThrownBy(() -> AuditActorRef.system(null))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("display");
    }

    @Test
    void systemActorFactoryDoesNotAcceptSourceReference() {
        // The system factory signature accepts only a display name, so a source
        // context, source type, or actor id cannot be supplied for a SYSTEM actor.
        AuditActorRef actor = AuditActorRef.system(DisplayName.of("System"));

        assertThat(actor.context()).isEmpty();
        assertThat(actor.sourceType()).isEmpty();
        assertThat(actor.id()).isEmpty();
    }

    @Test
    void validEntityReferenceIsAccepted() {
        AuditedEntityRef entity = AuditedEntityRef.of(
                SourceContext.of("publishing"), SourceType.of("content_item"), SourceEntityId.from(UUID.randomUUID()));

        assertThat(entity.context().value()).isEqualTo("publishing");
        assertThat(entity.type().value()).isEqualTo("content_item");
    }

    @Test
    void entityWithoutContextIsRejected() {
        assertThatThrownBy(() -> new AuditedEntityRef(null, SourceType.of("content_item"), SourceEntityId.from(UUID.randomUUID())))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("context");
    }

    @Test
    void entityWithoutTypeIsRejected() {
        assertThatThrownBy(() -> new AuditedEntityRef(SourceContext.of("publishing"), null, SourceEntityId.from(UUID.randomUUID())))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("type");
    }

    @Test
    void entityWithoutIdIsRejected() {
        assertThatThrownBy(() -> new AuditedEntityRef(SourceContext.of("publishing"), SourceType.of("content_item"), null))
                .isInstanceOf(AuditValidationException.class)
                .hasMessageContaining("id");
    }
}
