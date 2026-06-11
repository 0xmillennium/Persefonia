package dev.persefonia.contentpublishing.application.authorization;

import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import java.util.Objects;

public record ContentCommandActor(AdminIdentityRef identityRef, boolean active, boolean owner) {
    public ContentCommandActor {
        Objects.requireNonNull(identityRef, "identityRef");
    }
}
