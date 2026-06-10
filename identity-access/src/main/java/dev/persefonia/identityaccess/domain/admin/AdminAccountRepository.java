package dev.persefonia.identityaccess.domain.admin;

import java.util.Optional;

public interface AdminAccountRepository {
    AdminAccount save(AdminAccount account);

    Optional<AdminAccount> findById(AdminAccountId id);

    Optional<AdminAccount> findByOidcSubject(OidcSubject oidcSubject);

    Optional<AdminAccount> findByEmail(EmailAddress email);

    Optional<AdminAccount> findByNormalizedEmail(NormalizedEmailAddress normalizedEmail);

    boolean existsActiveOwner();

    long countAll();
}
