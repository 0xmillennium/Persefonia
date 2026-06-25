package dev.persefonia.webpublic.cv;

import dev.persefonia.profileportfolio.application.query.ActiveCvDownload;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicDownloadService;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import dev.persefonia.webpublic.insights.PublicInsightsObservationGateway;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public final class PublicCvController {
    private static final String DOWNLOAD_CACHE_CONTROL = "public, max-age=300, must-revalidate";

    private final ObjectProvider<ActiveCvPublicQueryService> queries;
    private final ObjectProvider<ActiveCvPublicDownloadService> downloads;
    private final PublicContentResponseHeaders responseHeaders;
    private final PublicContentViewModelFactory viewModelFactory;
    private final PublicCanonicalUrlFactory canonicalUrlFactory;
    private final PublicInsightsObservationGateway insights;

    public PublicCvController(
            ObjectProvider<ActiveCvPublicQueryService> queries,
            ObjectProvider<ActiveCvPublicDownloadService> downloads,
            PublicContentResponseHeaders responseHeaders,
            PublicContentViewModelFactory viewModelFactory,
            PublicCanonicalUrlFactory canonicalUrlFactory,
            PublicInsightsObservationGateway insights) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.downloads = Objects.requireNonNull(downloads, "downloads");
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
        this.viewModelFactory = Objects.requireNonNull(viewModelFactory, "viewModelFactory");
        this.canonicalUrlFactory = Objects.requireNonNull(canonicalUrlFactory, "canonicalUrlFactory");
        this.insights = Objects.requireNonNull(insights, "insights");
    }

    @GetMapping("/cv")
    public ModelAndView cv(HttpServletResponse response) {
        ActiveCvPublicQueryService queryService = queries.getIfAvailable();
        if (queryService == null) {
            return notFound(response);
        }
        return queryService.defaultLanguageView()
                .map(view -> render(view, "/cv", response))
                .orElseGet(() -> notFound(response));
    }

    @GetMapping("/cv/download")
    public ResponseEntity<InputStreamResource> cvDownload() {
        ActiveCvPublicDownloadService downloadService = downloads.getIfAvailable();
        if (downloadService == null) {
            return ResponseEntity.notFound().build();
        }
        return downloadService.defaultLanguageDownload()
                .map(this::observedDownloadResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/cv/{language}")
    public ModelAndView cvForLanguage(
            @PathVariable("language") String language,
            HttpServletResponse response) {
        ActiveCvPublicQueryService queryService = queries.getIfAvailable();
        if (queryService == null) {
            return notFound(response);
        }
        return queryService.explicitLanguageView(language)
                .map(view -> render(view, "/cv/" + language.toLowerCase(Locale.ROOT), response))
                .orElseGet(() -> notFound(response));
    }

    @GetMapping("/cv/{language}/download")
    public ResponseEntity<InputStreamResource> cvDownloadForLanguage(@PathVariable("language") String language) {
        ActiveCvPublicDownloadService downloadService = downloads.getIfAvailable();
        if (downloadService == null) {
            return ResponseEntity.notFound().build();
        }
        return downloadService.explicitLanguageDownload(language)
                .map(this::observedDownloadResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ModelAndView render(
            dev.persefonia.profileportfolio.application.query.ActiveCvPublicView view,
            String canonicalPath,
            HttpServletResponse response) {
        responseHeaders.applyPublicContentHeaders(response);
        insights.recordCvViewed();
        return new ModelAndView("site/cv/index", "page",
                PublicCvPage.from(view, canonicalUrlFactory.canonicalUrl(canonicalPath)));
    }

    private ModelAndView notFound(HttpServletResponse response) {
        responseHeaders.applyPublicNotFoundHeaders(response);
        insights.recordNotFound();
        ModelAndView modelAndView = new ModelAndView("site/not-found", "page", viewModelFactory.notFoundPage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    private static ResponseEntity<InputStreamResource> downloadResponse(ActiveCvDownload download) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, DOWNLOAD_CACHE_CONTROL)
                .body(new InputStreamResource(download.inputStream()));
    }

    private ResponseEntity<InputStreamResource> observedDownloadResponse(ActiveCvDownload download) {
        insights.recordCvDownloaded();
        return downloadResponse(download);
    }
}
