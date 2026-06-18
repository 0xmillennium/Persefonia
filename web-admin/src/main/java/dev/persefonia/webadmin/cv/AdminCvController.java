package dev.persefonia.webadmin.cv;

import dev.persefonia.profileportfolio.application.command.ActiveCvCommandError;
import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.exception.SitePresentationSettingsNotInitializedException;
import dev.persefonia.profileportfolio.application.query.ActiveCvAdminPageData;
import dev.persefonia.profileportfolio.application.service.ActiveCvAdminQueryService;
import dev.persefonia.profileportfolio.application.service.ActiveCvCommandGateway;
import dev.persefonia.webadmin.settings.PortfolioAdminActorResolver;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
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
public final class AdminCvController {
    private final ObjectProvider<ActiveCvAdminQueryService> queries;
    private final ObjectProvider<ActiveCvCommandGateway> commands;
    private final PortfolioAdminActorResolver actors;
    private final AdminCvPageChromeFactory chrome;
    private final AdminCvFormMapper mapper = new AdminCvFormMapper();
    private final AdminCvFormValidator validator = new AdminCvFormValidator();

    public AdminCvController(
            ObjectProvider<ActiveCvAdminQueryService> queries,
            ObjectProvider<ActiveCvCommandGateway> commands,
            PortfolioAdminActorResolver actors,
            AdminCvPageChromeFactory chrome) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/cv")
    public String edit(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "saved", required = false) String saved,
            Model model) {
        ActiveCvAdminPageData data = pageData();
        model.addAttribute("page", page(
                authentication,
                csrfToken,
                data,
                mapper.toForm(data),
                List.of(),
                List.of(),
                saved != null ? "Active CV saved." : null));
        return "admin/cv/index";
    }

    @PostMapping("/admin/cv")
    public String update(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminCvForm form,
            Model model) {
        ActiveCvAdminPageData data = pageData();
        List<AdminCvFieldError> errors = validator.validate(form);
        if (!errors.isEmpty()) {
            model.addAttribute("page", page(authentication, csrfToken, data, form, errors, List.of(), null));
            return "admin/cv/index";
        }
        ActiveCvUpdateResult result = commands().update(mapper.toCommand(
                actors.resolve(authentication),
                form,
                data.supportedLanguages(),
                Instant.now()));
        if (!result.errors().isEmpty()) {
            model.addAttribute("page", page(
                    authentication,
                    csrfToken,
                    data,
                    form,
                    result.errors().stream()
                            .map(AdminCvController::fieldError)
                            .toList(),
                    List.of(),
                    null));
            return "admin/cv/index";
        }
        return "redirect:/admin/cv?saved";
    }

    private ActiveCvAdminPageData pageData() {
        try {
            return queries().pageData();
        } catch (SitePresentationSettingsNotInitializedException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Site settings are not initialized.");
        }
    }

    private ActiveCvAdminQueryService queries() {
        ActiveCvAdminQueryService available = queries.getIfAvailable();
        if (available == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return available;
    }

    private ActiveCvCommandGateway commands() {
        ActiveCvCommandGateway available = commands.getIfAvailable();
        if (available == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return available;
    }

    private AdminCvPage page(
            Authentication authentication,
            CsrfToken csrfToken,
            ActiveCvAdminPageData data,
            AdminCvForm form,
            List<AdminCvFieldError> fieldErrors,
            List<String> globalErrors,
            String successMessage) {
        return new AdminCvPage(
                chrome.create(authentication, csrfToken),
                data,
                form,
                fieldErrors,
                globalErrors,
                successMessage);
    }

    private static AdminCvFieldError fieldError(ActiveCvCommandError error) {
        return new AdminCvFieldError(error.field(), error.message());
    }
}
