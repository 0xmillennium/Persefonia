package dev.persefonia.contentpublishing.application.support;

import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandAuthorizationPolicy;

public final class TestContentAuthorizationPolicy implements ContentCommandAuthorizationPolicy {
    private int checks;

    @Override
    public void requireOwner(ContentCommandActor actor, String commandName) {
        checks++;
        if (!actor.active() || !actor.owner()) {
            throw new SecurityException("OWNER authorization required for " + commandName);
        }
    }

    public int checks() {
        return checks;
    }
}
