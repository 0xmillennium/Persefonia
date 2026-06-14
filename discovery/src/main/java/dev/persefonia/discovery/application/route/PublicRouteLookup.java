package dev.persefonia.discovery.application.route;

import dev.persefonia.discovery.application.contract.PublicUrl;
import java.util.Objects;

public record PublicRouteLookup(PublicUrl publicUrl) {
    public PublicRouteLookup {
        Objects.requireNonNull(publicUrl, "publicUrl");
    }
}
