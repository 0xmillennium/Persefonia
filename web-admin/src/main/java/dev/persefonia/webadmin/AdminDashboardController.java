package dev.persefonia.webadmin;

import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class AdminDashboardController {
    private final AuthenticatedAdminViewResolver authenticatedAdminViewResolver;

    public AdminDashboardController(AuthenticatedAdminViewResolver authenticatedAdminViewResolver) {
        this.authenticatedAdminViewResolver =
                Objects.requireNonNull(authenticatedAdminViewResolver, "authenticatedAdminViewResolver");
    }

    @GetMapping("/admin")
    public String dashboard(
            Authentication authentication,
            CsrfToken csrfToken,
            Model model) {
        AuthenticatedAdminView admin = authenticatedAdminViewResolver.resolve(authentication);
        if (csrfToken == null) {
            throw new IllegalStateException("CSRF token is required for the admin shell");
        }

        LogoutFormViewModel logoutForm =
                new LogoutFormViewModel("/logout", csrfToken.getParameterName(), csrfToken.getToken());
        model.addAttribute("page", AdminDashboardViewModel.shell(admin, logoutForm));
        return "admin/shell";
    }
}
