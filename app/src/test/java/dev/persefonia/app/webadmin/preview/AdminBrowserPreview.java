package dev.persefonia.app.webadmin.preview;

import dev.persefonia.app.PersefoniaApplication;
import dev.persefonia.app.security.admin.AdminPrincipal;
import dev.persefonia.app.security.oidc.PersefoniaOidcUser;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminAccountRepository;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/** Test-classpath-only browser fixture. Never packaged in the application BootJar. */
public final class AdminBrowserPreview {
    private AdminBrowserPreview() {}

    public static void main(String[] args) {
        var application = new SpringApplication(PersefoniaApplication.class, PreviewConfiguration.class);
        application.setAdditionalProfiles("test", "admin-browser-preview");
        application.run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("admin-browser-preview")
    public static class PreviewConfiguration {
        @Bean
        FilterRegistrationBean<OncePerRequestFilter> syntheticPreviewSession(
                Environment environment, AdminAccountRepository accounts, SecurityContextRepository contexts) {
            if (!"127.0.0.1".equals(environment.getProperty("server.address"))
                    || !environment.getProperty("persefonia.preview.disposable", Boolean.class, false)
                    || environment.matchesProfiles("prod", "production")
                    || !environment.getProperty("spring.datasource.url", "")
                            .matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/persefonia_browser")) {
                throw new IllegalStateException("Admin browser preview requires explicit loopback disposable test data");
            }
            var filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                        FilterChain chain) throws ServletException, IOException {
                    if (!request.getRequestURI().equals("/__admin_preview/session")) {
                        chain.doFilter(request, response);
                        return;
                    }
                    response.setHeader("Cache-Control", "no-store");
                    if (!request.getMethod().equals("POST")
                            || !"synthetic".equals(request.getHeader("X-Persefonia-Preview"))
                            || !request.getRemoteAddr().equals("127.0.0.1")) {
                        response.setStatus(404);
                        return;
                    }
                    var namespace = request.getHeader("X-Persefonia-Preview-Case");
                    if (namespace == null) namespace = "legacy";
                    if (!namespace.matches("[a-z0-9-]{1,32}")) {
                        response.setStatus(404);
                        return;
                    }
                    var role = "editor".equals(request.getParameter("role")) ? AdminRole.EDITOR : AdminRole.OWNER;
                    var caseNamespace = namespace;
                    var subject = OidcSubject.of("synthetic-browser-" + role.name().toLowerCase(java.util.Locale.ROOT) + "-" + caseNamespace);
                    var now = Instant.now();
                    var account = accounts.findByOidcSubject(subject).orElseGet(() -> accounts.save(AdminAccount.create(
                            AdminAccountId.newId(), subject,
                            EmailAddress.of(role.name().toLowerCase(java.util.Locale.ROOT) + "+" + caseNamespace + "@preview.invalid"),
                            DisplayName.of("Synthetic preview " + role.name()), Set.of(role), now)));
                    var delegate = new DefaultOidcUser(Set.of(), new OidcIdToken("synthetic-preview-token",
                            now, now.plusSeconds(3600), Map.of("sub", subject.value())), "sub");
                    var user = new PersefoniaOidcUser(delegate, AdminPrincipal.from(account));
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "synthetic-preview"));
                    contexts.saveContext(context, request, response);
                    response.setHeader("Cache-Control", "no-store");
                    response.setStatus(204);
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
            return registration;
        }
    }
}
