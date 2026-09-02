package dev.persefonia.webadmin.discovery;

import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public final class AdminRedirectController {
    private static final String LIST_COMMAND = "discovery.redirect.list";
    private static final String CREATE_COMMAND = "discovery.redirect.create";
    private static final String DEACTIVATE_COMMAND = "discovery.redirect.deactivate";

    private final AdminRedirectGateway redirects;
    private final AdminRedirectAccessPolicy access;
    private final AdminRedirectActorResolver actors;
    private final AdminRedirectPageChromeFactory chrome;

    public AdminRedirectController(
            AdminRedirectGateway redirects,
            AdminRedirectAccessPolicy access,
            AdminRedirectActorResolver actors,
            AdminRedirectPageChromeFactory chrome) {
        this.redirects = Objects.requireNonNull(redirects, "redirects");
        this.access = Objects.requireNonNull(access, "access");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
    }

    @GetMapping("/admin/discovery/redirects")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "created", required = false) String created,
            @RequestParam(name = "noop", required = false) String noop,
            @RequestParam(name = "deactivated", required = false) String deactivated,
            @RequestParam(name = "alreadyInactive", required = false) String alreadyInactive,
            @RequestParam(name = "notFound", required = false) String notFound,
            Model model) {
        access.requireOwner(authentication, LIST_COMMAND);
        model.addAttribute("page", page(
                authentication,
                csrfToken,
                new AdminRedirectForm(),
                List.of(),
                List.of(),
                successMessage(created, noop, deactivated, alreadyInactive, notFound)));
        return "admin/discovery/redirects";
    }

    @PostMapping("/admin/discovery/redirects")
    public String create(
            Authentication authentication,
            CsrfToken csrfToken,
            @ModelAttribute AdminRedirectForm form,
            Model model) {
        access.requireOwner(authentication, CREATE_COMMAND);
        var actor = actors.resolve(authentication);
        return switch (redirects.create(actor, form)) {
            case AdminRedirectCreateResult.Created ignored -> redirect("created");
            case AdminRedirectCreateResult.Noop ignored -> redirect("noop");
            case AdminRedirectCreateResult.Rejected rejected -> {
                model.addAttribute("page", page(
                        authentication,
                        csrfToken,
                        form,
                        rejected.fieldErrors(),
                        rejected.globalErrors(),
                        null));
                yield "admin/discovery/redirects";
            }
        };
    }

    @PostMapping("/admin/discovery/redirects/{redirectRuleId}/deactivate")
    public String deactivate(Authentication authentication, @PathVariable("redirectRuleId") String redirectRuleId) {
        access.requireOwner(authentication, DEACTIVATE_COMMAND);
        var actor = actors.resolve(authentication);
        return switch (redirects.deactivate(actor, redirectRuleId)) {
            case DEACTIVATED -> redirect("deactivated");
            case ALREADY_INACTIVE -> redirect("alreadyInactive");
            case NOT_FOUND -> redirect("notFound");
        };
    }

    private AdminRedirectPage page(
            Authentication authentication,
            CsrfToken csrfToken,
            AdminRedirectForm form,
            List<AdminRedirectFieldError> fieldErrors,
            List<String> globalErrors,
            String successMessage) {
        return new AdminRedirectPage(
                chrome.create(authentication, csrfToken),
                form,
                fieldErrors,
                globalErrors,
                redirects.list().rules(),
                successMessage);
    }

    private static String successMessage(
            String created,
            String noop,
            String deactivated,
            String alreadyInactive,
            String notFound) {
        if (created != null) {
            return "Redirect created.";
        }
        if (noop != null) {
            return "Matching active redirect already exists.";
        }
        if (deactivated != null) {
            return "Redirect deactivated.";
        }
        if (alreadyInactive != null) {
            return "Redirect was already inactive.";
        }
        if (notFound != null) {
            return "Redirect was not found.";
        }
        return null;
    }

    private static String redirect(String flag) {
        return "redirect:/admin/discovery/redirects?" + flag + "=true";
    }
}
