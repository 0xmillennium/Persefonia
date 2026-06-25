package dev.persefonia.webpublic.contact;

import dev.persefonia.webpublic.FrontendAssetResolver;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public final class PublicContactController {
    private static final String FRONTEND_ENTRY = "src/main.ts";
    private static final String TITLE = "Contact";
    private static final String DESCRIPTION = "Send a private message through the public contact form.";
    private static final int CHEAP_FIELD_LIMIT = 6000;

    private final PublicContactSubmissionGateway submissions;
    private final FrontendAssetResolver assetResolver;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final PublicContentResponseHeaders responseHeaders;

    public PublicContactController(
            PublicContactSubmissionGateway submissions,
            FrontendAssetResolver assetResolver,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            PublicContentResponseHeaders responseHeaders) {
        this.submissions = Objects.requireNonNull(submissions, "submissions must not be null");
        this.assetResolver = Objects.requireNonNull(assetResolver, "assetResolver must not be null");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory must not be null");
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders must not be null");
    }

    @GetMapping("/contact")
    public ModelAndView contact(
            @RequestParam(name = "submitted", required = false) String submitted,
            HttpServletRequest request,
            HttpServletResponse response) {
        responseHeaders.applyPublicContactHeaders(response);
        return page(new ContactForm(), Map.of(), successMessage(submitted), "1".equals(submitted), request);
    }

    @PostMapping("/contact")
    public Object submit(
            @ModelAttribute ContactForm form,
            HttpServletRequest request,
            HttpServletResponse response) {
        responseHeaders.applyPublicContactHeaders(response);

        Map<String, String> cheapErrors = cheapErrors(form);
        if (!cheapErrors.isEmpty()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return page(form, cheapErrors, "Please correct the highlighted fields.", false, request);
        }

        PublicContactSubmissionResult result = submissions.submit(new PublicContactSubmissionRequest(
                form.senderName(),
                form.senderEmail(),
                form.subject(),
                form.body(),
                request.getRemoteAddr()));

        return switch (result.status()) {
            case SUCCESS -> {
                RedirectView redirect = new RedirectView("/contact?submitted=1");
                redirect.setStatusCode(HttpStatus.SEE_OTHER);
                yield redirect;
            }
            case VALIDATION_FAILED -> {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                yield page(form, result.fieldErrors(), "Please correct the highlighted fields.", false, request);
            }
            case RATE_LIMITED -> {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                yield page(form, Map.of(), "Please wait before sending another message.", false, request);
            }
            case TEMPORARILY_UNAVAILABLE -> {
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                yield page(form, Map.of(), "The contact form is temporarily unavailable. Please try again later.", false, request);
            }
        };
    }

    private ModelAndView page(
            ContactForm form,
            Map<String, String> fieldErrors,
            String message,
            boolean submitted,
            HttpServletRequest request) {
        return new ModelAndView("site/contact/index", "page", new ContactPageViewModel(
                TITLE,
                DESCRIPTION,
                canonicalUrlFactory.canonicalUrl("/contact"),
                assetResolver.stylesheetPaths(FRONTEND_ENTRY),
                form,
                fieldErrors,
                message,
                submitted,
                csrfToken(request)));
    }

    private static Map<String, String> cheapErrors(ContactForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        rejectHuge(errors, "senderName", form.senderName());
        rejectHuge(errors, "senderEmail", form.senderEmail());
        rejectHuge(errors, "subject", form.subject());
        rejectHuge(errors, "body", form.body());
        return errors;
    }

    private static void rejectHuge(Map<String, String> errors, String field, String value) {
        if (value != null && value.length() > CHEAP_FIELD_LIMIT) {
            errors.put(field, "This field is too long.");
        }
    }

    private static String successMessage(String submitted) {
        return "1".equals(submitted) ? "Your message was received." : null;
    }

    private static CsrfToken csrfToken(HttpServletRequest request) {
        Object token = request.getAttribute(CsrfToken.class.getName());
        if (token instanceof CsrfToken csrfToken) {
            return csrfToken;
        }
        throw new IllegalStateException("CSRF token is unavailable");
    }
}
