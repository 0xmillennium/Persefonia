package dev.persefonia.webadmin.settings;

public final class AdminSiteSettingsForm {
    private String siteName = "";
    private String defaultLanguage = "TR";
    private boolean supportedTr;
    private boolean supportedEn;
    private String titleSuffix = "";
    private String defaultMetaDescription = "";
    private String defaultTheme = "SYSTEM";
    private boolean showFeaturedProjects;
    private boolean showLatestWriting;
    private boolean showResearchHighlights;
    private String featuredProjectLimit = "3";
    private String latestWritingLimit = "5";

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = value(siteName);
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = value(defaultLanguage);
    }

    public boolean isSupportedTr() {
        return supportedTr;
    }

    public void setSupportedTr(boolean supportedTr) {
        this.supportedTr = supportedTr;
    }

    public boolean isSupportedEn() {
        return supportedEn;
    }

    public void setSupportedEn(boolean supportedEn) {
        this.supportedEn = supportedEn;
    }

    public String getTitleSuffix() {
        return titleSuffix;
    }

    public void setTitleSuffix(String titleSuffix) {
        this.titleSuffix = value(titleSuffix);
    }

    public String getDefaultMetaDescription() {
        return defaultMetaDescription;
    }

    public void setDefaultMetaDescription(String defaultMetaDescription) {
        this.defaultMetaDescription = value(defaultMetaDescription);
    }

    public String getDefaultTheme() {
        return defaultTheme;
    }

    public void setDefaultTheme(String defaultTheme) {
        this.defaultTheme = value(defaultTheme);
    }

    public boolean isShowFeaturedProjects() {
        return showFeaturedProjects;
    }

    public void setShowFeaturedProjects(boolean showFeaturedProjects) {
        this.showFeaturedProjects = showFeaturedProjects;
    }

    public boolean isShowLatestWriting() {
        return showLatestWriting;
    }

    public void setShowLatestWriting(boolean showLatestWriting) {
        this.showLatestWriting = showLatestWriting;
    }

    public boolean isShowResearchHighlights() {
        return showResearchHighlights;
    }

    public void setShowResearchHighlights(boolean showResearchHighlights) {
        this.showResearchHighlights = showResearchHighlights;
    }

    public String getFeaturedProjectLimit() {
        return featuredProjectLimit;
    }

    public void setFeaturedProjectLimit(String featuredProjectLimit) {
        this.featuredProjectLimit = value(featuredProjectLimit);
    }

    public String getLatestWritingLimit() {
        return latestWritingLimit;
    }

    public void setLatestWritingLimit(String latestWritingLimit) {
        this.latestWritingLimit = value(latestWritingLimit);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
