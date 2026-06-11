package dev.persefonia.webadmin.content;

public final class AdminContentForm {
    private String type = "";
    private String language = "";
    private String visibility = "";
    private String slug = "";
    private String title = "";
    private String summary = "";
    private String markdownSource = "";
    private String metaTitle = "";
    private String metaDescription = "";
    private String canonicalPath = "";
    private String ogTitle = "";
    private String ogDescription = "";
    private String ogImageAssetId = "";

    public String getType() { return type; }
    public void setType(String type) { this.type = value(type); }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = value(language); }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = value(visibility); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = value(slug); }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = value(title); }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = value(summary); }
    public String getMarkdownSource() { return markdownSource; }
    public void setMarkdownSource(String markdownSource) { this.markdownSource = value(markdownSource); }
    public String getMetaTitle() { return metaTitle; }
    public void setMetaTitle(String metaTitle) { this.metaTitle = value(metaTitle); }
    public String getMetaDescription() { return metaDescription; }
    public void setMetaDescription(String metaDescription) { this.metaDescription = value(metaDescription); }
    public String getCanonicalPath() { return canonicalPath; }
    public void setCanonicalPath(String canonicalPath) { this.canonicalPath = value(canonicalPath); }
    public String getOgTitle() { return ogTitle; }
    public void setOgTitle(String ogTitle) { this.ogTitle = value(ogTitle); }
    public String getOgDescription() { return ogDescription; }
    public void setOgDescription(String ogDescription) { this.ogDescription = value(ogDescription); }
    public String getOgImageAssetId() { return ogImageAssetId; }
    public void setOgImageAssetId(String ogImageAssetId) { this.ogImageAssetId = value(ogImageAssetId); }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
