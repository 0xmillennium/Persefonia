package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.service.ContentAdminQueryService;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public final class AdminContentListController {
    private final ContentAdminQueryService queries;
    private final ContentAdminActorResolver actors;
    private final AdminContentPageChromeFactory chrome;
    private final AdminContentViewModelFactory views;

    public AdminContentListController(
            ContentAdminQueryService queries,
            ContentAdminActorResolver actors,
            AdminContentPageChromeFactory chrome,
            AdminContentViewModelFactory views) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.views = Objects.requireNonNull(views, "views");
    }

    @GetMapping("/admin/content")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @RequestParam(name = "archived", required = false) String archived,
            Model model) {
        var actor = actors.resolve(authentication);
        model.addAttribute("page", views.list(
                chrome.create(authentication, csrfToken),
                queries.listEditableContent(actor),
                archived != null ? "Content archived." : null));
        return "admin/content/list";
    }
}
