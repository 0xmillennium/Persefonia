package dev.persefonia.medialibrary.application.recovery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MediaRecoveryInventoryPage(
        List<MediaRecoveryObjectReference> items,
        MediaRecoveryCursor nextCursor) {
    public MediaRecoveryInventoryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.isEmpty() && nextCursor != null) {
            throw new IllegalArgumentException("empty recovery page cannot have a next cursor");
        }
    }
    public Optional<MediaRecoveryCursor> nextCursorOptional() {
        return Optional.ofNullable(nextCursor);
    }
}
