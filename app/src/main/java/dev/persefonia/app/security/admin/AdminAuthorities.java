package dev.persefonia.app.security.admin;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class AdminAuthorities {
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_OWNER = "ROLE_OWNER";
    public static final String ROLE_EDITOR = "ROLE_EDITOR";

    private AdminAuthorities() {
    }

    public static Set<GrantedAuthority> from(AdminPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
        if (principal.isOwner()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_OWNER));
        }
        if (principal.isEditor()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_EDITOR));
        }
        return Collections.unmodifiableSet(authorities);
    }
}
