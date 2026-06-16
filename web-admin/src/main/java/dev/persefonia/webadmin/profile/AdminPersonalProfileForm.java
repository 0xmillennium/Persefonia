package dev.persefonia.webadmin.profile;

public final class AdminPersonalProfileForm {
    private String displayName = "";
    private boolean trEnabled;
    private String trShortBio = "";
    private String trLongBio = "";
    private String trLocationText = "";
    private String trTechnicalFocusAreas = "";
    private String trEducationSummaries = "";
    private String trCurrentFocusItems = "";
    private boolean enEnabled;
    private String enShortBio = "";
    private String enLongBio = "";
    private String enLocationText = "";
    private String enTechnicalFocusAreas = "";
    private String enEducationSummaries = "";
    private String enCurrentFocusItems = "";
    private String externalLinks = "";

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = value(displayName);
    }

    public boolean isTrEnabled() {
        return trEnabled;
    }

    public void setTrEnabled(boolean trEnabled) {
        this.trEnabled = trEnabled;
    }

    public String getTrShortBio() {
        return trShortBio;
    }

    public void setTrShortBio(String trShortBio) {
        this.trShortBio = value(trShortBio);
    }

    public String getTrLongBio() {
        return trLongBio;
    }

    public void setTrLongBio(String trLongBio) {
        this.trLongBio = value(trLongBio);
    }

    public String getTrLocationText() {
        return trLocationText;
    }

    public void setTrLocationText(String trLocationText) {
        this.trLocationText = value(trLocationText);
    }

    public String getTrTechnicalFocusAreas() {
        return trTechnicalFocusAreas;
    }

    public void setTrTechnicalFocusAreas(String trTechnicalFocusAreas) {
        this.trTechnicalFocusAreas = value(trTechnicalFocusAreas);
    }

    public String getTrEducationSummaries() {
        return trEducationSummaries;
    }

    public void setTrEducationSummaries(String trEducationSummaries) {
        this.trEducationSummaries = value(trEducationSummaries);
    }

    public String getTrCurrentFocusItems() {
        return trCurrentFocusItems;
    }

    public void setTrCurrentFocusItems(String trCurrentFocusItems) {
        this.trCurrentFocusItems = value(trCurrentFocusItems);
    }

    public boolean isEnEnabled() {
        return enEnabled;
    }

    public void setEnEnabled(boolean enEnabled) {
        this.enEnabled = enEnabled;
    }

    public String getEnShortBio() {
        return enShortBio;
    }

    public void setEnShortBio(String enShortBio) {
        this.enShortBio = value(enShortBio);
    }

    public String getEnLongBio() {
        return enLongBio;
    }

    public void setEnLongBio(String enLongBio) {
        this.enLongBio = value(enLongBio);
    }

    public String getEnLocationText() {
        return enLocationText;
    }

    public void setEnLocationText(String enLocationText) {
        this.enLocationText = value(enLocationText);
    }

    public String getEnTechnicalFocusAreas() {
        return enTechnicalFocusAreas;
    }

    public void setEnTechnicalFocusAreas(String enTechnicalFocusAreas) {
        this.enTechnicalFocusAreas = value(enTechnicalFocusAreas);
    }

    public String getEnEducationSummaries() {
        return enEducationSummaries;
    }

    public void setEnEducationSummaries(String enEducationSummaries) {
        this.enEducationSummaries = value(enEducationSummaries);
    }

    public String getEnCurrentFocusItems() {
        return enCurrentFocusItems;
    }

    public void setEnCurrentFocusItems(String enCurrentFocusItems) {
        this.enCurrentFocusItems = value(enCurrentFocusItems);
    }

    public String getExternalLinks() {
        return externalLinks;
    }

    public void setExternalLinks(String externalLinks) {
        this.externalLinks = value(externalLinks);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
