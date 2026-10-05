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
    public String mobileNavigation() { return text("Mobile primary", "Mobil ana gezinme"); }
    public String menu() { return text("Menu", "Menü"); }
    public String projects() { return text("Projects", "Projeler"); }
    public String writing() { return text("Writing", "Yazılar"); }
    public String research() { return text("Research", "Araştırma"); }
    public String about() { return text("About", "Hakkımda"); }
    public String contact() { return text("Contact", "İletişim"); }
    public String identity() { return text("Identity", "Kimlik"); }
    public String selectedWork() { return text("Selected work", "Seçilmiş çalışmalar"); }
    public String architecture() { return text("Architecture", "Mimari"); }
    public String heroKicker() { return text("Software engineering · Research · Writing", "Yazılım mühendisliği · Araştırma · Yazılar"); }
    public String heroTitleStart() { return text("Building systems", "Anlatmaya değer"); }
    public String heroTitleEmphasis() { return text("worth explaining.", "sistemler kurmak."); }
    public String heroIntro() { return text("A personal space for software architecture, projects, research, writing, and the engineering decisions behind them.", "Yazılım mimarisi, projeler, araştırmalar, yazılar ve bunların ardındaki mühendislik kararları için kişisel bir alan."); }
    public String exploreWork() { return text("Explore work", "Çalışmaları incele"); }
    public String readWriting() { return text("Read the latest writing", "Son yazıları oku"); }
    public String workTitle() { return text("Work, in context.", "Bağlamıyla çalışmalar."); }
    public String workIntro() { return text("Public projects, with the questions and decisions behind them.", "Açık projeler ve arkalarındaki sorularla kararlar."); }
    public String exploreProject() { return text("Explore project", "Projeyi incele"); }
    public String projectsEmpty() { return text("Public projects will appear here when available.", "Açık projeler hazır olduğunda burada yer alacak."); }
    public String viewProjects() { return text("View all projects", "Tüm projeleri gör"); }
    public String writingTitle() { return text("Thinking in public.", "Düşünceleri paylaşmak."); }
    public String writingIntro() { return text("Notes and essays make the reasoning visible after the work is done.", "Notlar ve yazılar, işin ardındaki düşünme sürecini görünür kılar."); }
    public String writingEmpty() { return text("Published writing will appear here when available.", "Yayımlanan yazılar hazır olduğunda burada yer alacak."); }
    public String searchPublished() { return text("Search published content", "Yayımlanan içerikte ara"); }
    public String researchTitle() { return text("Questions in motion.", "Soruların peşinde."); }
    public String researchIntro() { return text("Explorations connect observations, experiments, and open questions.", "Araştırmalar gözlemleri, deneyleri ve açık soruları bir araya getirir."); }
    public String researchRecord() { return text("Research / Public record", "Araştırma / Açık kayıt"); }
    public String researchEmpty() { return text("Published research will appear here when available.", "Yayımlanan araştırmalar hazır olduğunda burada yer alacak."); }
    public String architectureTitle() { return text("The system behind the work.", "Çalışmaların ardındaki sistem."); }
    public String architectureIntro() { return text("Persefonia is a publishing platform and a modular monolith. Explore the boundaries behind this public website.", "Persefonia bir yayın platformu ve modüler monolittir. Bu açık web sitesinin ardındaki sınırları inceleyin."); }
    public String architectureLabel() { return text("Persefonia's public experience, application, domain, and durable state", "Persefonia'nın açık deneyimi, uygulaması, alanı ve kalıcı verisi"); }
    public String publicExperience() { return text("Public experience", "Açık deneyim"); }
    public String publicExperienceDetail() { return text("Pages, routes, and public access", "Sayfalar, yollar ve açık erişim"); }
    public String application() { return text("Application", "Uygulama"); }
    public String applicationDetail() { return text("Use cases and decisions", "Kullanım senaryoları ve kararlar"); }
    public String domain() { return text("Domain", "Alan"); }
    public String domainDetail() { return text("Bounded contexts", "Sınırlandırılmış bağlamlar"); }
    public String durableState() { return text("Durable state", "Kalıcı veri"); }
    public String durableStateDetail() { return text("PostgreSQL and media", "PostgreSQL ve medya"); }
    public String aboutTitle() { return text("Back to the person.", "Yeniden insana."); }
    public String currentFocus() { return text("Current focus", "Güncel odak"); }
    public String contactTitle() { return text("Let’s continue the conversation.", "Sohbeti sürdürelim."); }
    public String contactIntro() { return text("Questions, ideas, and thoughtful collaboration are welcome.", "Sorulara, fikirlere ve özenli iş birliklerine açığım."); }
    public String getInTouch() { return text("Get in touch", "İletişime geç"); }
    public String journey() { return text("Person → Work → Ideas → System → Person", "İnsan → Çalışma → Fikirler → Sistem → İnsan"); }
    public String projectTechnologies() { return text("Project technologies", "Proje teknolojileri"); }
    public String pauseMotion() { return text("Pause motion", "Hareketi durdur"); }
    public String resumeMotion() { return text("Resume motion", "Hareketi sürdür"); }
    public String systemColophon() { return text("System colophon", "Sistem künyesi"); }
    public String inspectSystem() { return text("Open a layer to explore its role.", "İşlevini incelemek için bir katmanı açın."); }
    public String publicExperienceEvidence() { return text("Spring MVC and JTE render semantic HTML. Vite manages self-hosted assets. The public content remains readable without JavaScript.", "Spring MVC ve JTE anlamsal HTML üretir. Vite, kendi sunucumuzdaki varlıkları yönetir. Açık içerik JavaScript olmadan da okunabilir."); }
    public String applicationEvidence() { return text("Application services orchestrate use cases and authorize commands. The web layer uses public application contracts.", "Uygulama servisleri kullanım senaryolarını yönetir ve komutları yetkilendirir. Web katmanı açık uygulama sözleşmelerini kullanır."); }
    public String domainEvidence() { return text("Bounded contexts keep business rules independent of web and persistence infrastructure. Discovery owns the public projections.", "Sınırlandırılmış bağlamlar iş kurallarını web ve kalıcılık altyapısından bağımsız tutar. Açık izdüşümler Discovery tarafından yönetilir."); }
    public String durableStateEvidence() { return text("PostgreSQL is the durable source of truth, with Flyway migrations and JDBC persistence. Redis supports auxiliary, short-lived state.", "PostgreSQL, Flyway geçişleri ve JDBC kalıcılığıyla kalıcı doğruluk kaynağıdır. Redis yardımcı, kısa ömürlü durumu destekler."); }
    public String backToTop() { return text("Back to top", "Başa dön"); }
    public String personalSite() { return text("Personal website / Publishing platform", "Kişisel web sitesi / Yayın platformu"); }
    public String by() { return text("by", "hazırlayan"); }
}
