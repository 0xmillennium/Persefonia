package dev.persefonia.webadmin.cv;

import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.command.ActiveCvSelectionInput;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.query.ActiveCvAdminPageData;
import java.time.Instant;
import java.util.List;

public final class AdminCvFormMapper {
    public AdminCvForm toForm(ActiveCvAdminPageData pageData) {
        AdminCvForm form = new AdminCvForm();
        pageData.selections().forEach(selection -> {
            if ("TR".equals(selection.language())) {
                form.setTrAssetId(selection.mediaAssetId() == null ? "" : selection.mediaAssetId().toString());
                form.setTrDisplayLabel(selection.displayLabel());
            } else if ("EN".equals(selection.language())) {
                form.setEnAssetId(selection.mediaAssetId() == null ? "" : selection.mediaAssetId().toString());
                form.setEnDisplayLabel(selection.displayLabel());
            }
        });
        return form;
    }

    public UpdateActiveCvCommand toCommand(
            PortfolioCommandActor actor,
            AdminCvForm form,
            List<String> supportedLanguages,
            Instant requestedAt) {
        return new UpdateActiveCvCommand(
                actor,
                supportedLanguages.stream()
                        .map(language -> new ActiveCvSelectionInput(
                                language,
                                assetId(form, language),
                                displayLabel(form, language)))
                        .toList(),
                requestedAt);
    }

    private static String assetId(AdminCvForm form, String language) {
        return switch (language) {
            case "TR" -> form.getTrAssetId();
            case "EN" -> form.getEnAssetId();
            default -> "";
        };
    }

    private static String displayLabel(AdminCvForm form, String language) {
        return switch (language) {
            case "TR" -> form.getTrDisplayLabel();
            case "EN" -> form.getEnDisplayLabel();
            default -> "";
        };
    }
}
