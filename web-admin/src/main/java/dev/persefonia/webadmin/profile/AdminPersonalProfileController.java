package dev.persefonia.webadmin.profile;

import dev.persefonia.profileportfolio.application.exception.PersonalProfileApplicationException;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.AdminPersonalProfileView;
import dev.persefonia.profileportfolio.application.service.PersonalProfileAdminQueryService;
import dev.persefonia.profileportfolio.application.service.PersonalProfileCommandGateway;
import dev.persefonia.webadmin.settings.PortfolioAdminActorResolver;
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
public final class AdminPersonalProfileController {
    private final PersonalProfileAdminQueryService queries;
    private final PersonalProfileCommandGateway commands;
    private final PortfolioAdminActorResolver actors;
    private final AdminPersonalProfilePageChromeFactory chrome;
    private final AdminPersonalProfileFormMapper mapper = new AdminPersonalProfileFormMapper();
    private final AdminPersonalProfileFormValidator validator = new AdminPersonalProfileFormValidator();

    public AdminPersonalProfileController(
            PersonalProfileAdminQueryService queries,
            PersonalProfileCommandGateway commands,
            PortfolioAdminActorResolver actors,
            AdminPersonalProfilePageChromeFactory chrome) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/profile")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "saved", required = false) String saved,
            Model model) {
        AdminPersonalProfileView view = currentView();
        model.addAttribute("page", page(
                authentication,
                csrfToken,
                mapper.toForm(view),
                view.defaultLanguage(),
                !view.profileExists(),
                List.of(),
                List.of(),
                saved != null ? "Profile saved." : null));
        return "admin/profile/edit";
    }

    @PostMapping("/admin/profile")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminPersonalProfileForm form,
            Model model) {
        AdminPersonalProfileView view = currentView();
        List<AdminPersonalProfileFieldError> errors = validator.validate(form, view.defaultLanguage());
        if (!errors.isEmpty()) {
            model.addAttribute("page", page(
                    authentication,
                    csrfToken,
                    form,
                    view.defaultLanguage(),
                    !view.profileExists(),
                    errors,
                    List.of(),
                    null));
            return "admin/profile/edit";
        }
        try {
            commands.upsertActive(mapper.toCommand(actors.resolve(authentication), form, Instant.now()));
            return "redirect:/admin/profile?saved";
        } catch (PersonalProfileApplicationException exception) {
            model.addAttribute("page", page(
                    authentication,
                    csrfToken,
                    form,
                    view.defaultLanguage(),
                    !view.profileExists(),
                    List.of(),
                    List.of("Profile could not be saved."),
                    null));
            return "admin/profile/edit";
        }
    }

    private AdminPersonalProfileView currentView() {
        try {
            return queries.current();
        } catch (SitePresentationSettingsNotInitializedException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Site settings are not initialized.");
        }
    }

    private AdminPersonalProfilePage page(
            Authentication authentication,
            CsrfToken csrfToken,
            AdminPersonalProfileForm form,
            String defaultLanguage,
            boolean onboarding,
            List<AdminPersonalProfileFieldError> fieldErrors,
            List<String> globalErrors,
            String successMessage) {
        return new AdminPersonalProfilePage(
                chrome.create(authentication, csrfToken),
                form,
                defaultLanguage,
                onboarding,
                fieldErrors,
                globalErrors,
                successMessage);
    }
}
