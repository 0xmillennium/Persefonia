package dev.persefonia.webadmin.settings;

import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.query.AdminSitePresentationSettingsView;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AdminSiteSettingsFormMapper {
    public AdminSiteSettingsForm toForm(AdminSitePresentationSettingsView view) {
        AdminSiteSettingsForm form = new AdminSiteSettingsForm();
        form.setSiteName(view.siteName());
        form.setDefaultLanguage(view.defaultLanguage());
        form.setSupportedTr(view.supportedLanguages().contains("TR"));
        form.setSupportedEn(view.supportedLanguages().contains("EN"));
        form.setTitleSuffix(view.titleSuffix());
        form.setDefaultMetaDescription(view.defaultMetaDescription());
        form.setDefaultTheme(view.defaultTheme());
        form.setShowFeaturedProjects(view.showFeaturedProjects());
        form.setShowLatestWriting(view.showLatestWriting());
        form.setShowResearchHighlights(view.showResearchHighlights());
        form.setFeaturedProjectLimit(Integer.toString(view.featuredProjectLimit()));
        form.setLatestWritingLimit(Integer.toString(view.latestWritingLimit()));
        return form;
    }

    public UpdateSitePresentationSettingsCommand toCommand(
            PortfolioCommandActor actor,
            AdminSiteSettingsForm form,
            int featuredProjectLimit,
            int latestWritingLimit,
            Instant requestedAt) {
        return new UpdateSitePresentationSettingsCommand(
                actor,
                form.getSiteName(),
                form.getDefaultLanguage(),
                supportedLanguages(form),
                form.getTitleSuffix(),
                form.getDefaultMetaDescription(),
                form.getDefaultTheme(),
                form.isShowFeaturedProjects(),
                form.isShowLatestWriting(),
                form.isShowResearchHighlights(),
                featuredProjectLimit,
                latestWritingLimit,
                requestedAt);
    }

    private static Set<String> supportedLanguages(AdminSiteSettingsForm form) {
        Set<String> languages = new LinkedHashSet<>();
        if (form.isSupportedTr()) {
            languages.add("TR");
        }
        if (form.isSupportedEn()) {
            languages.add("EN");
        }
        return languages;
    }
}
