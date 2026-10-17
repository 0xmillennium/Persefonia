package dev.persefonia.webpublic;

import java.util.Objects;

/** Static homepage copy, selected from the same public language as promoted content. */
public record PublicHomePageCopy(String language) {
    public PublicHomePageCopy {
        if (!"EN".equals(language) && !"TR".equals(language)) {
            throw new IllegalArgumentException("Unsupported homepage language: " + language);
        }
    }

    public static PublicHomePageCopy forLanguage(String language) {
        return new PublicHomePageCopy(Objects.requireNonNull(language, "language"));
    }

    private String text(String en, String tr) { return "TR".equals(language) ? tr : en; }

    public String skipLink() { return text("Skip to main content", "Ana içeriğe geç"); }
    public String primaryNavigation() { return text("Primary", "Ana gezinme"); }
    public String projects() { return text("Projects", "Projeler"); }
    public String writing() { return text("Writing", "Yazılar"); }
    public String collections() { return text("Collections", "Koleksiyonlar"); }
    public String articles() { return text("Articles", "Makaleler"); }
    public String notes() { return text("Notes", "Notlar"); }
    public String articlesEmpty() { return text("No published articles yet.", "Henüz yayımlanmış makale yok."); }
    public String notesEmpty() { return text("No published notes yet.", "Henüz yayımlanmış not yok."); }
    public String research() { return text("Research", "Araştırma"); }
    public String contact() { return text("Contact", "İletişim"); }
    public String workTitle() { return text("Work, in context.", "Bağlamıyla çalışmalar."); }
    public String projectsEmpty() { return text("Public projects will appear here when available.", "Açık projeler hazır olduğunda burada yer alacak."); }
    public String searchPublished() { return text("Search published content", "Yayımlanan içerikte ara"); }
    public String researchEmpty() { return text("Published research will appear here when available.", "Yayımlanan araştırmalar hazır olduğunda burada yer alacak."); }
    public String contactTitle() { return text("Let’s continue the conversation.", "Sohbeti sürdürelim."); }
    public String getInTouch() { return text("Get in touch", "İletişime geç"); }
    public String projectTechnologies() { return text("Project technologies", "Proje teknolojileri"); }
    public String backToTop() { return text("Back to top", "Başa dön"); }
}
