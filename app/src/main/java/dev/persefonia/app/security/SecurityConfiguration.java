package dev.persefonia.app.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.PermissionsPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

import dev.persefonia.app.security.oidc.PersefoniaOidcUserService;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
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

    @Bean
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<PersefoniaOidcUserService> oidcUserServices) throws Exception {
        http
                .securityMatcher(new NegatedRequestMatcher(EndpointRequest.toAnyEndpoint()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/assets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth2/authorization/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/login/oauth2/code/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/logout").authenticated()
                        .requestMatchers("/admin", "/admin/**").authenticated()
                        .requestMatchers(HttpMethod.GET, PUBLIC_CONTENT_GET_PATTERNS).permitAll()
                        .anyRequest().denyAll())
                .csrf(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
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
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(oidcUserService)));
            }
        }

        return http.build();
    }
}
