package dev.persefonia.webadmin.settings;

import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsApplicationException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsAdminQueryService;
import dev.persefonia.profileportfolio.application.service.SitePresentationSettingsCommandGateway;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminSiteSettingsController {
    private final SitePresentationSettingsCommandGateway commands;
    private final SitePresentationSettingsAdminQueryService queries;
    private final PortfolioAdminActorResolver actors;
    private final AdminSiteSettingsPageChromeFactory chrome;
    private final AdminSiteSettingsFormMapper mapper = new AdminSiteSettingsFormMapper();
    private final AdminSiteSettingsFormValidator validator = new AdminSiteSettingsFormValidator();

    public AdminSiteSettingsController(
            SitePresentationSettingsCommandGateway commands,
            SitePresentationSettingsAdminQueryService queries,
            PortfolioAdminActorResolver actors,
            AdminSiteSettingsPageChromeFactory chrome) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/settings/site")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "saved", required = false) String saved,
            Model model) {
        try {
            model.addAttribute("page", page(
                    authentication,
                    csrfToken,
                    mapper.toForm(queries.current()),
                    List.of(),
                    List.of(),
                    saved != null ? "Settings saved." : null));
            return "admin/settings/site";
        } catch (SitePresentationSettingsNotInitializedException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Site settings are not initialized.");
        }
    }

    @PostMapping("/admin/settings/site")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminSiteSettingsForm form,
            Model model) {
        List<AdminSiteSettingsFieldError> errors = validator.validate(form);
        if (!errors.isEmpty()) {
            model.addAttribute("page", page(authentication, csrfToken, form, errors, List.of(), null));
            return "admin/settings/site";
        }
        try {
            commands.update(mapper.toCommand(
                    actors.resolve(authentication),
                    form,
                    validator.featuredProjectLimit(form),
                    validator.latestWritingLimit(form),
                    Instant.now()));
            return "redirect:/admin/settings/site?saved";
        } catch (SitePresentationSettingsApplicationException exception) {
            model.addAttribute("page", page(
                    authentication,
                    csrfToken,
                    form,
                    List.of(),
                    List.of("Settings could not be saved."),
                    null));
            return "admin/settings/site";
        }
    }

    private AdminSiteSettingsPage page(
            Authentication authentication,
            CsrfToken csrfToken,
            AdminSiteSettingsForm form,
            List<AdminSiteSettingsFieldError> fieldErrors,
            List<String> globalErrors,
            String successMessage) {
        return new AdminSiteSettingsPage(
                chrome.create(authentication, csrfToken), form, fieldErrors, globalErrors, successMessage);
    }
}
