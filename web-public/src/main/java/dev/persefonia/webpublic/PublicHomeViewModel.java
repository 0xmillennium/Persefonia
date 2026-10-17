package dev.persefonia.webpublic;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantView;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.persefonia.profileportfolio.application.query.PublicProfileSummaryView;
import dev.persefonia.profileportfolio.application.query.PublicFeaturedProjectView;
import dev.persefonia.discovery.application.index.PublicHomepageContentEntry;
import java.util.List;
import java.util.Optional;

public record PublicHomeViewModel(
        String siteName,
        String pageTitle,
        String defaultLanguage,
        String defaultMetaDescription,
        String ownerAlias,
        String canonicalUrl,
        PublicHomePageCopy copy,
        Optional<PublicProfileSummaryView> profile,
        boolean promoteFeaturedProjects,
        List<PublicFeaturedProjectView> featuredProjects,
        String featuredProjectsTitle,
        boolean promoteLatestWriting,
        boolean promoteResearchHighlights,
        List<PublicHomepageContentEntry> latestWriting,
        List<PublicHomepageContentEntry> articles,
        List<PublicHomepageContentEntry> notes,
        List<PublicHomepageContentEntry> researchHighlights,
        Map<String, PublicImageVariantView> galleryCovers,
        Map<String, List<String>> galleryTags,
        String scriptPath,
        List<String> stylesheetPaths) {
    public PublicHomeViewModel {
        galleryCovers = Map.copyOf(galleryCovers);
        galleryTags = galleryTags.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        profile = profile == null ? Optional.empty() : profile;
        featuredProjects = List.copyOf(featuredProjects);
        latestWriting = List.copyOf(latestWriting);
        articles = List.copyOf(articles);
        notes = List.copyOf(notes);
        researchHighlights = List.copyOf(researchHighlights);
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        Objects.requireNonNull(featuredProjectsTitle, "featuredProjectsTitle");
        stylesheetPaths = List.copyOf(stylesheetPaths);
    }

    public record WritingCollection(String id, String title, String emptyMessage,
            boolean enabled, List<PublicHomepageContentEntry> entries) {}

    public List<WritingCollection> writingCollections() {
        return List.of(
                new WritingCollection("articles", copy.articles(), copy.articlesEmpty(), promoteLatestWriting, articles),
                new WritingCollection("notes", copy.notes(), copy.notesEmpty(), promoteLatestWriting, notes),
                new WritingCollection("research", copy.research(), copy.researchEmpty(), promoteResearchHighlights, researchHighlights));
    }

    public Optional<PublicImageVariantView> projectNavigationCover() {
        return featuredProjects.stream().map(project -> galleryCovers.get(project.publicUrl()))
                .filter(Objects::nonNull).findFirst();
    }

    public Optional<PublicImageVariantView> writingNavigationCover() {
        return latestWriting.stream().map(entry -> galleryCovers.get(entry.publicUrl()))
                .filter(Objects::nonNull).findFirst();
    }
}
