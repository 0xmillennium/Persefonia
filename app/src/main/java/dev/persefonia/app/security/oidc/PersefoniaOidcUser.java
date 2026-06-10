package dev.persefonia.app.security.oidc;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import dev.persefonia.app.security.admin.AdminAuthorities;
import dev.persefonia.app.security.admin.AdminPrincipal;

public final class PersefoniaOidcUser implements OidcUser {
    private final OidcUser delegate;
    private final AdminPrincipal adminPrincipal;
    private final Set<GrantedAuthority> authorities;

    public PersefoniaOidcUser(OidcUser delegate, AdminPrincipal adminPrincipal) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.adminPrincipal = Objects.requireNonNull(adminPrincipal, "adminPrincipal");
        this.authorities = AdminAuthorities.from(adminPrincipal);
    }

    public AdminPrincipal adminPrincipal() {
        return adminPrincipal;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return adminPrincipal.accountId().value().toString();
    }
}
