package dev.persefonia.app.audit.integration;

public final class AuditActionCatalog {
    public static final String CONTENT_DRAFT_CREATED = "content.draft.created";
    public static final String CONTENT_DRAFT_UPDATED = "content.draft.updated";
    public static final String CONTENT_PUBLISHED = "content.published";
    public static final String CONTENT_UNPUBLISHED = "content.unpublished";
    public static final String CONTENT_ARCHIVED = "content.archived";
    public static final String CONTENT_TAGS_REPLACED = "content.tags.replaced";
    public static final String TAG_CREATED = "tag.created";
    public static final String TAG_UPDATED = "tag.updated";
    public static final String TAG_ARCHIVED = "tag.archived";
    public static final String SERIES_CREATED = "series.created";
    public static final String SERIES_UPDATED = "series.updated";
    public static final String SERIES_ARCHIVED = "series.archived";
    public static final String SERIES_ENTRY_ADDED = "series.entry.added";
    public static final String SERIES_ENTRY_REMOVED = "series.entry.removed";
    public static final String SERIES_ENTRIES_REORDERED = "series.entries.reordered";
    public static final String TRANSLATION_GROUP_CREATED = "translation_group.created";
    public static final String TRANSLATION_GROUP_ENTRY_ADDED = "translation_group.entry.added";
    public static final String TRANSLATION_GROUP_ENTRY_REMOVED = "translation_group.entry.removed";
    public static final String PROJECT_CREATED = "project.created";
    public static final String PROJECT_UPDATED = "project.updated";
    public static final String PROFILE_UPSERTED = "profile.upserted";
    public static final String SITE_SETTINGS_UPDATED = "site_settings.updated";
    public static final String CV_ACTIVE_UPDATED = "cv.active.updated";
    public static final String ASSET_UPLOADED = "asset.uploaded";
    public static final String ASSET_METADATA_UPDATED = "asset.metadata.updated";
    public static final String REDIRECT_CREATED = "redirect.created";
    public static final String REDIRECT_DEACTIVATED = "redirect.deactivated";
    public static final String CONTACT_MESSAGE_STATUS_CHANGED = "contact_message.status.changed";
    public static final String ADMIN_ACCOUNT_BOOTSTRAPPED = "admin_account.bootstrapped";

    private AuditActionCatalog() {
    }
}
