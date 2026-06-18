package dev.persefonia.webadmin.media;

public final class AdminMediaMetadataForm {
    private String visibility = "PRIVATE";
    private String altText = "";
    private boolean decorative;

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public boolean isDecorative() {
        return decorative;
    }

    public void setDecorative(boolean decorative) {
        this.decorative = decorative;
    }
}
