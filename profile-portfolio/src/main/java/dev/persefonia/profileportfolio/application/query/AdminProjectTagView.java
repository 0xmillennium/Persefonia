package dev.persefonia.profileportfolio.application.query;

import java.util.UUID;

public record AdminProjectTagView(UUID id, String name, String slug, boolean archived) {
}
