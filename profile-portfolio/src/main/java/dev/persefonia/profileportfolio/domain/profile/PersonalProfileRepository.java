package dev.persefonia.profileportfolio.domain.profile;

import java.util.Optional;

public interface PersonalProfileRepository {
    PersonalProfile save(PersonalProfile profile);

    Optional<PersonalProfile> findById(ProfileId id);

    Optional<PersonalProfile> findActiveProfile();
}
