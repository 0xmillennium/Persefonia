package dev.persefonia.webadmin.taxonomy;

public final class AdminTagForm {
    private String name = "";
    private String slug = "";
    private String description = "";

    public String getName() { return name; }
    public void setName(String name) { this.name = value(name); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = value(slug); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = value(description); }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
