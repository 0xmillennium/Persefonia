package dev.persefonia.app.security;

import java.time.Clock;
import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.PermissionsPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

import dev.persefonia.app.security.oidc.AdminOidcAuthenticationSuccessHandler;
import dev.persefonia.app.security.oidc.AdminOidcSessionProperties;
import dev.persefonia.app.security.oidc.AdminOidcSessionRevalidationFilter;
import dev.persefonia.app.security.oidc.AdminOidcSessionRevalidationService;
import dev.persefonia.app.security.oidc.PersefoniaOidcUserService;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(AdminOidcSessionProperties.class)
public class SecurityConfiguration {
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                    + "object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";
    static final String[] PUBLIC_CONTENT_GET_PATTERNS = {
            "/tr/articles/*",
            "/en/articles/*",
            "/tr/notes/*",
            "/en/notes/*",
            "/tr/research/*",
            "/en/research/*",
            "/tr/pages/*",
            "/en/pages/*"
    };
    static final String[] PUBLIC_TAG_GET_PATTERNS = {
            "/tr/tags/*",
            "/en/tags/*"
    };
    static final String[] PUBLIC_SERIES_GET_PATTERNS = {
            "/tr/series/*",
            "/en/series/*"
    };
    static final String[] PUBLIC_PROJECT_GET_PATTERNS = {
            "/tr/projects",
            "/en/projects",
            "/tr/projects/*",
            "/en/projects/*"
    };

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<PersefoniaOidcUserService> oidcUserServices,
            ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClients,
            ObjectProvider<AdminOidcAuthenticationSuccessHandler> successHandlers,
            ObjectProvider<AdminOidcSessionRevalidationService> revalidationServices,
            AdminOidcSessionProperties sessionProperties,
            SecurityContextRepository contexts,
            ObjectProvider<Clock> clocks) throws Exception {
        http
                .securityMatcher(new NegatedRequestMatcher(EndpointRequest.toAnyEndpoint()))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/sitemap.xml", "/robots.txt").permitAll()
                        .requestMatchers(HttpMethod.GET, "/feed.xml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/contact").permitAll()
                        .requestMatchers(HttpMethod.POST, "/contact").permitAll()
                        .requestMatchers(HttpMethod.GET, "/cv", "/cv/download", "/cv/*", "/cv/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/assets/*/variants/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth2/authorization/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/login/oauth2/code/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/admin/analytics").hasRole("OWNER")
                        .requestMatchers("/admin/audit", "/admin/audit/**").hasRole("OWNER")
                        .requestMatchers("/admin/operations", "/admin/operations/**").hasRole("OWNER")
                        .requestMatchers("/admin", "/admin/**").authenticated()
                        .requestMatchers(HttpMethod.GET, PUBLIC_CONTENT_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_TAG_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_SERIES_GET_PATTERNS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_PROJECT_GET_PATTERNS).permitAll()
                        .anyRequest().denyAll())
                .securityContext(securityContext -> securityContext.securityContextRepository(contexts))
                .csrf(Customizer.withDefaults())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .headers(headers -> headers
                        .addHeaderWriter(new ContentSecurityPolicyHeaderWriter(CONTENT_SECURITY_POLICY))
                        .addHeaderWriter(new PermissionsPolicyHeaderWriter(PERMISSIONS_POLICY))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));

        if (clientRegistrations.getIfAvailable() != null) {
            PersefoniaOidcUserService oidcUserService = oidcUserServices.getIfAvailable();
            if (oidcUserService != null) {
                http.oauth2Login(oauth2 -> oauth2
                        .authorizedClientRepository(authorizedClients.getObject())
                        .successHandler(successHandlers.getObject())
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(oidcUserService)));
                http.addFilterBefore(new AdminOidcSessionRevalidationFilter(
                        sessionProperties, revalidationServices.getObject(), clocks.getIfAvailable(Clock::systemUTC), contexts),
                        AuthorizationFilter.class);
            }
        }

        return http.build();
    }
}
