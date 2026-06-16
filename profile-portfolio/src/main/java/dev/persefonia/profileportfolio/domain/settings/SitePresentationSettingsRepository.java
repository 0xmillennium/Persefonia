package dev.persefonia.profileportfolio.domain.settings;

import java.util.Optional;

public interface SitePresentationSettingsRepository {
    SitePresentationSettings save(SitePresentationSettings settings);

    Optional<SitePresentationSettings> findCurrent();

    Optional<SitePresentationSettings> findById(SitePresentationSettingsId id);
}
