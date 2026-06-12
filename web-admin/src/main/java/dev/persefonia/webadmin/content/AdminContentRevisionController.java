package dev.persefonia.webadmin.content;

import dev.persefonia.contentpublishing.application.exception.ContentNotFoundException;
import dev.persefonia.contentpublishing.application.query.ListContentRevisionsQuery;
import dev.persefonia.contentpublishing.application.service.ContentRevisionQueryHandler;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public final class AdminContentRevisionController {
    private static final String NOT_FOUND = "Content was not found.";

    private final ContentRevisionQueryHandler queries;
    private final ContentAdminActorResolver actors;
    private final AdminContentPageChromeFactory chrome;
    private final AdminContentRevisionViewModelFactory views;

    public AdminContentRevisionController(
            ContentRevisionQueryHandler queries,
            ContentAdminActorResolver actors,
            AdminContentPageChromeFactory chrome,
            AdminContentRevisionViewModelFactory views) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.chrome = Objects.requireNonNull(chrome, "chrome");
        this.views = Objects.requireNonNull(views, "views");
    }

    @GetMapping("/admin/content/{contentId}/revisions")
    public String list(
            Authentication authentication,
            CsrfToken csrfToken,
            @PathVariable("contentId") String contentId,
            Model model) {
        try {
            var history = queries.history(new ListContentRevisionsQuery(
                    actors.resolve(authentication), parseContentId(contentId)));
            model.addAttribute("page", views.list(chrome.create(authentication, csrfToken), history));
            return "admin/content/revisions";
        } catch (ContentNotFoundException exception) {
            throw notFound();
        }
    }

    private static ContentId parseContentId(String value) {
        try {
            return ContentId.from(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }
}
