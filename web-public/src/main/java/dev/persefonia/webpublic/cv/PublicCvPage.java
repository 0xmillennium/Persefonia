package dev.persefonia.webpublic.cv;

import dev.persefonia.profileportfolio.application.query.ActiveCvPublicView;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public record PublicCvPage(
        String title,
        String language,
        String displayLabel,
        String downloadPath,
        String displayFilename,
        String contentType,
        String sizeLabel,
        String selectedAt,
        String assetUpdatedAt) {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public PublicCvPage {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(displayLabel, "displayLabel");
        Objects.requireNonNull(downloadPath, "downloadPath");
        Objects.requireNonNull(displayFilename, "displayFilename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(sizeLabel, "sizeLabel");
        Objects.requireNonNull(selectedAt, "selectedAt");
        Objects.requireNonNull(assetUpdatedAt, "assetUpdatedAt");
    }

    static PublicCvPage from(ActiveCvPublicView view) {
        return new PublicCvPage(
                "CV",
                view.language(),
                view.displayLabel(),
                view.downloadPath(),
                view.displayFilename(),
                view.contentType(),
                sizeLabel(view.sizeBytes()),
                DATE_FORMATTER.format(view.selectedAt()),
                DATE_FORMATTER.format(view.assetUpdatedAt()));
    }

    private static String sizeLabel(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        long kib = Math.round(sizeBytes / 1024.0);
        return kib + " KB";
    }
}
