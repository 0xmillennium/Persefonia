package dev.persefonia.app.webadmin.projects;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.webadmin.projects.AdminProjectFieldError;
import dev.persefonia.webadmin.projects.AdminProjectForm;
import dev.persefonia.webadmin.projects.AdminProjectFormValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminProjectFormValidatorTest {
    private final AdminProjectFormValidator validator = new AdminProjectFormValidator();

    @Test
    void acceptsValidPrivateExperimentProject() {
        AdminProjectForm form = validForm();

        assertThat(validator.validate(form, "TR")).isEmpty();
    }

    @Test
    void rejectsInvalidSlugAndFeaturedRules() {
        AdminProjectForm form = validForm();
        form.setStatus("ARCHIVED");
        form.setFeatured(true);
        form.setTrEnabled(false);
        form.setEnEnabled(true);
        form.setEnSlug("Bad Slug");
        form.setEnTitle("Title");
        form.setEnSummary("Summary");

        assertThat(fields(validator.validate(form, "TR")))
                .contains("enSlug", "featured");
    }

    @Test
    void validatesTechnologySyntaxCategoryAndDuplicates() {
        AdminProjectForm form = validForm();
        form.setTechnologies("""
                Java | LANGUAGE
                Broken
                Java | LANGUAGE
                Redis | CACHE
                """);

        assertThat(messages(validator.validate(form, "TR")))
                .anyMatch(message -> message.contains("Technologies line 2 must use"))
                .anyMatch(message -> message.contains("Technologies line 3 duplicates"))
                .anyMatch(message -> message.contains("Technologies line 4 has an invalid category"));
    }

    @Test
    void validatesLinkSyntaxTypeAndExternalUrlPolicy() {
        AdminProjectForm form = validForm();
        form.setLinks("""
                Source | https://example.test | SOURCE
                Broken
                FTP | ftp://example.test | SOURCE
                Demo | https://example.test | PREVIEW
                """);

        assertThat(messages(validator.validate(form, "TR")))
                .anyMatch(message -> message.contains("Links line 2 must use"))
                .anyMatch(message -> message.contains("Links line 3 must use a valid http or https URL"))
                .anyMatch(message -> message.contains("Links line 4 has an invalid type"));
    }

    @Test
    void validatesTagUuidAndLimitBeforeMapperRuns() {
        AdminProjectForm form = validForm();
        List<String> tags = new ArrayList<>();
        for (int index = 0; index <= AdminProjectFormValidator.MAX_TAGS; index++) {
            tags.add(UUID.randomUUID().toString());
        }
        tags.add("not-a-uuid");
        form.setTagIds(tags);

        assertThat(messages(validator.validate(form, "TR")))
                .contains("Projects may have at most 12 tags.", "Selected tags include an invalid id.");
    }

    private static AdminProjectForm validForm() {
        AdminProjectForm form = new AdminProjectForm();
        form.setStatus("EXPERIMENT");
        form.setVisibility("PRIVATE");
        form.setTrEnabled(true);
        form.setTrSlug("sample-project");
        form.setTrTitle("Sample Project");
        form.setTrSummary("Summary");
        form.setTechnologies("Java | LANGUAGE");
        form.setLinks("Source | https://example.test | SOURCE");
        return form;
    }

    private static List<String> fields(List<AdminProjectFieldError> errors) {
        return errors.stream().map(error -> error.field()).toList();
    }

    private static List<String> messages(List<AdminProjectFieldError> errors) {
        return errors.stream().map(error -> error.message()).toList();
    }
}
