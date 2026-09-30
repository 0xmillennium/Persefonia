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
        form.setStandardPublicTheme(view.standardPublicTheme());
        form.setPromoteFeaturedProjects(view.promoteFeaturedProjects());
        form.setPromoteLatestWriting(view.promoteLatestWriting());
        form.setPromoteResearchHighlights(view.promoteResearchHighlights());
        form.setFeaturedProjectLimit(Integer.toString(view.featuredProjectLimit()));
        form.setLatestWritingLimit(Integer.toString(view.latestWritingLimit()));
        form.setResearchHighlightLimit(Integer.toString(view.researchHighlightLimit()));
        return form;
    }

    public UpdateSitePresentationSettingsCommand toCommand(
            PortfolioCommandActor actor,
            AdminSiteSettingsForm form,
            int featuredProjectLimit,
            int latestWritingLimit,
            int researchHighlightLimit,
            Instant requestedAt) {
        return new UpdateSitePresentationSettingsCommand(
                actor,
                form.getSiteName(),
                form.getDefaultLanguage(),
                supportedLanguages(form),
                form.getTitleSuffix(),
                form.getDefaultMetaDescription(),
                form.getStandardPublicTheme(),
                form.isPromoteFeaturedProjects(),
                form.isPromoteLatestWriting(),
                form.isPromoteResearchHighlights(),
                featuredProjectLimit,
                latestWritingLimit,
                researchHighlightLimit,
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
