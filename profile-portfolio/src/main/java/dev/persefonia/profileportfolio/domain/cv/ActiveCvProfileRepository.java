package dev.persefonia.profileportfolio.domain.cv;

import java.util.Optional;

public interface ActiveCvProfileRepository {
    Optional<ActiveCvProfile> findSingleton();

    ActiveCvProfile save(ActiveCvProfile profile);
}
