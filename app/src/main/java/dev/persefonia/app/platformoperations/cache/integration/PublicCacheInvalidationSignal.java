package dev.persefonia.app.platformoperations.cache.integration;

import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.publicview.ContentPublicMutationFacts;
import dev.persefonia.contentpublishing.application.publicview.ContentTagMutationFacts;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.profileportfolio.application.publicview.ProjectPublicMutationFacts;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import java.util.Objects;
import java.util.UUID;

public sealed interface PublicCacheInvalidationSignal {
    String kind();

    UUID sourceId();

    record ContentChanged(ContentPublicMutationFacts facts) implements PublicCacheInvalidationSignal {
        public ContentChanged { Objects.requireNonNull(facts, "facts"); }
        @Override public String kind() { return "content.changed"; }
        @Override public UUID sourceId() { return facts.contentId().value(); }
    }

    record ContentTagsChanged(ContentTagMutationFacts facts) implements PublicCacheInvalidationSignal {
        public ContentTagsChanged { Objects.requireNonNull(facts, "facts"); }
        @Override public String kind() { return "content.tags-changed"; }
        @Override public UUID sourceId() { return facts.contentId().value(); }
    }

    record SeriesChanged(SeriesChange change, SeriesResult result) implements PublicCacheInvalidationSignal {
        public SeriesChanged { Objects.requireNonNull(change, "change"); Objects.requireNonNull(result, "result"); }
        @Override public String kind() { return "series." + change.name().toLowerCase(); }
        @Override public UUID sourceId() { return result.seriesId().value(); }
    }

    record TranslationGroupChanged(
            TranslationChange change, TranslationGroupResult result) implements PublicCacheInvalidationSignal {
        public TranslationGroupChanged {
            Objects.requireNonNull(change, "change"); Objects.requireNonNull(result, "result");
        }
        @Override public String kind() { return "translation-group." + change.name().toLowerCase(); }
        @Override public UUID sourceId() { return result.translationGroupId().value(); }
    }

    record TagChanged(TagChange change, TagCommandResult result) implements PublicCacheInvalidationSignal {
        public TagChanged { Objects.requireNonNull(change, "change"); Objects.requireNonNull(result, "result"); }
        @Override public String kind() { return "tag." + change.name().toLowerCase(); }
        @Override public UUID sourceId() { return result.tagId().value(); }
    }

    record ProjectChanged(ProjectPublicMutationFacts facts) implements PublicCacheInvalidationSignal {
        public ProjectChanged { Objects.requireNonNull(facts, "facts"); }
        @Override public String kind() { return "project.changed"; }
        @Override public UUID sourceId() { return facts.projectId(); }
    }

    record PersonalProfileChanged(UUID sourceId) implements PublicCacheInvalidationSignal {
        public PersonalProfileChanged { Objects.requireNonNull(sourceId, "sourceId"); }
        @Override public String kind() { return "personal-profile.changed"; }
    }

    record SiteSettingsChanged(UUID sourceId) implements PublicCacheInvalidationSignal {
        public SiteSettingsChanged { Objects.requireNonNull(sourceId, "sourceId"); }
        @Override public String kind() { return "site-settings.changed"; }
    }

    record ActiveCvChanged(UUID sourceId) implements PublicCacheInvalidationSignal {
        public ActiveCvChanged { Objects.requireNonNull(sourceId, "sourceId"); }
        @Override public String kind() { return "active-cv.changed"; }
    }

    record AssetVisibilityChanged(
            AssetId assetId, AssetVisibility beforeVisibility, AssetVisibility afterVisibility)
            implements PublicCacheInvalidationSignal {
        public AssetVisibilityChanged {
            Objects.requireNonNull(assetId, "assetId");
            Objects.requireNonNull(beforeVisibility, "beforeVisibility");
            Objects.requireNonNull(afterVisibility, "afterVisibility");
        }
        @Override public String kind() { return "asset.visibility-changed"; }
        @Override public UUID sourceId() { return assetId.value(); }
    }

    record RedirectChanged(UUID sourceId, PublicUrl sourceUrl) implements PublicCacheInvalidationSignal {
        public RedirectChanged { Objects.requireNonNull(sourceId, "sourceId"); Objects.requireNonNull(sourceUrl, "sourceUrl"); }
        @Override public String kind() { return "redirect.changed"; }
    }

    enum SeriesChange { CREATE, UPDATE, ARCHIVE, ENTRY_ADD, ENTRY_REMOVE, ENTRY_REORDER }
    enum TranslationChange { ADD, REMOVE }
    enum TagChange { CREATE, UPDATE, ARCHIVE }
}
