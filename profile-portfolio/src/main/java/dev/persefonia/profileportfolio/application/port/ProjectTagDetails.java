package dev.persefonia.profileportfolio.application.port;

import java.util.UUID;

public record ProjectTagDetails(UUID id, String name, String slug, boolean archived) {
}
