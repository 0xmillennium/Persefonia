package dev.persefonia.app.audit.integration;

public final class AuditEntityCatalog {
    public static final Entity CONTENT_ITEM = new Entity("publishing", "content_item");
    public static final Entity SERIES = new Entity("publishing", "series");
    public static final Entity TRANSLATION_GROUP = new Entity("publishing", "translation_group");
    public static final Entity TAG = new Entity("taxonomy", "tag");
    public static final Entity PROJECT = new Entity("portfolio", "project");
    public static final Entity PERSONAL_PROFILE = new Entity("portfolio", "personal_profile");
    public static final Entity SITE_PRESENTATION_SETTINGS =
            new Entity("portfolio", "site_presentation_settings");
    public static final Entity ACTIVE_CV_PROFILE = new Entity("portfolio", "active_cv_profile");
    public static final Entity ASSET = new Entity("media", "asset");
    public static final Entity REDIRECT_RULE = new Entity("discovery", "redirect_rule");
    public static final Entity CONTACT_MESSAGE = new Entity("communication", "contact_message");
    public static final Entity ADMIN_ACCOUNT = new Entity("iam", "admin_account");

    private AuditEntityCatalog() {
    }

    public record Entity(String context, String type) {
    }
}
