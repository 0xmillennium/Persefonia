package dev.persefonia.profileportfolio.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.profileportfolio.application.publicview.ProjectPublicExposurePolicy;
import dev.persefonia.profileportfolio.domain.project.ProjectStatus;
import dev.persefonia.profileportfolio.domain.project.ProjectVisibility;
import org.junit.jupiter.api.Test;

class ProjectPublicExposurePolicyTest {
    private final ProjectPublicExposurePolicy policy = new ProjectPublicExposurePolicy();

    @Test
    void publicActiveProjectIsListedAndMayBeFeatured() {
        var exposure = policy.snapshot(ProjectStatus.ACTIVE, ProjectVisibility.PUBLIC, true);
        assertThat(exposure.directReachable()).isTrue();
        assertThat(exposure.listed()).isTrue();
        assertThat(exposure.sitemapEligible()).isTrue();
        assertThat(exposure.homepageFeaturedEligible()).isTrue();
    }

    @Test
    void archivedAndUnlistedRemainDirectWhilePrivateDoesNot() {
        assertThat(policy.snapshot(ProjectStatus.ARCHIVED, ProjectVisibility.PUBLIC, false).directReachable()).isTrue();
        assertThat(policy.snapshot(ProjectStatus.ARCHIVED, ProjectVisibility.PUBLIC, false).listed()).isFalse();
        assertThat(policy.snapshot(ProjectStatus.ACTIVE, ProjectVisibility.UNLISTED, false).directReachable()).isTrue();
        assertThat(policy.snapshot(ProjectStatus.ACTIVE, ProjectVisibility.UNLISTED, false).listed()).isFalse();
        assertThat(policy.snapshot(ProjectStatus.ACTIVE, ProjectVisibility.PRIVATE, false).directReachable()).isFalse();
    }
}
