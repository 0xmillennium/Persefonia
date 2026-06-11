package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.exception.ContentApplicationException;
import dev.persefonia.contentpublishing.application.service.ContentCommandService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public final class AdminContentDraftController {
    private static final String SAFE_CREATE_ERROR = "The draft could not be created.";

    private final ContentCommandService commands;
    private final ContentAdminActorResolver actors;
    private final AdminContentPageChromeFactory chrome;
    private final AdminContentFormValidator validator;
    private final AdminContentFormMapper mapper;
    private final AdminContentViewModelFactory views;

    public AdminContentDraftController(
            ContentCommandService commands,
            ContentAdminActorResolver actors,
            AdminContentPageChromeFactory chrome,
            AdminContentFormValidator validator,
            AdminContentFormMapper mapper,
            AdminContentViewModelFactory views) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.views = Objects.requireNonNull(views, "views");
    }

    @GetMapping("/admin/content/new")
    public String form(Authentication authentication, CsrfToken csrfToken, Model model) {
        model.addAttribute("page", views.create(chrome.create(authentication, csrfToken), new AdminContentForm()));
        return "admin/content/form";
    }

    @PostMapping("/admin/content")
    public String create(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminContentForm form,
            Model model) {
        var page = views.create(chrome.create(authentication, csrfToken), form);
        var errors = validator.validate(form, true);
        if (!errors.isEmpty()) {
            model.addAttribute("page", views.withErrors(page, errors, List.of()));
            return "admin/content/form";
        }
        try {
            var result = commands.createDraft(mapper.toCreate(actors.resolve(authentication), form, Instant.now()));
            return "redirect:/admin/content/" + result.contentId().value() + "/edit?created";
        } catch (ContentApplicationException | IllegalArgumentException exception) {
            model.addAttribute("page", views.withErrors(page, List.of(), List.of(SAFE_CREATE_ERROR)));
            return "admin/content/form";
        }
    }
}
