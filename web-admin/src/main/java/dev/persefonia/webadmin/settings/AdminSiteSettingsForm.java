package dev.persefonia.webadmin.settings;

public final class AdminSiteSettingsForm {
    private String siteName = "";
    private String defaultLanguage = "TR";
    private boolean supportedTr;
    private boolean supportedEn;
    private String titleSuffix = "";
    private String defaultMetaDescription = "";
    private String standardPublicTheme = "SYSTEM";
    private boolean promoteFeaturedProjects;
    private boolean promoteLatestWriting;
    private boolean promoteResearchHighlights;
    private String featuredProjectLimit = "3";
    private String latestWritingLimit = "3";
    private String researchHighlightLimit = "3";

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

    public String getStandardPublicTheme() {
        return standardPublicTheme;
    }

    public void setStandardPublicTheme(String standardPublicTheme) {
        this.standardPublicTheme = value(standardPublicTheme);
    }

    public boolean isPromoteFeaturedProjects() {
        return promoteFeaturedProjects;
    }

    public void setPromoteFeaturedProjects(boolean promoteFeaturedProjects) {
        this.promoteFeaturedProjects = promoteFeaturedProjects;
    }

    public boolean isPromoteLatestWriting() {
        return promoteLatestWriting;
    }

    public void setPromoteLatestWriting(boolean promoteLatestWriting) {
        this.promoteLatestWriting = promoteLatestWriting;
    }

    public boolean isPromoteResearchHighlights() {
        return promoteResearchHighlights;
    }

    public void setPromoteResearchHighlights(boolean promoteResearchHighlights) {
        this.promoteResearchHighlights = promoteResearchHighlights;
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

    public String getResearchHighlightLimit() {
        return researchHighlightLimit;
    }

    public void setResearchHighlightLimit(String researchHighlightLimit) {
        this.researchHighlightLimit = value(researchHighlightLimit);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
