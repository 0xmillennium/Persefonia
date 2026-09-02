package dev.persefonia.discovery.application.redirect;

import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import java.util.Objects;

public record CreateManualRedirectCommand(
        AdminRedirectCommandActor actor,
        PublicUrl sourceUrl,
        PublicUrl targetUrl,
        RedirectStatusCode statusCode) {
    public CreateManualRedirectCommand {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(statusCode, "statusCode");
    }
}
