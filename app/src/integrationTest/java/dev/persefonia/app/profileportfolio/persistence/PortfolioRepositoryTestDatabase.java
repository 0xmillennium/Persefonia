package dev.persefonia.app.profileportfolio.persistence;

import dev.persefonia.profileportfolio.domain.profile.PersonalProfileRepository;
import dev.persefonia.profileportfolio.domain.project.ProjectRepository;
import dev.persefonia.profileportfolio.domain.cv.ActiveCvProfileRepository;
import dev.persefonia.profileportfolio.domain.settings.SitePresentationSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.persefonia.app.testsupport.SharedPostgresSpringIntegrationTest;

abstract class PortfolioRepositoryTestDatabase extends SharedPostgresSpringIntegrationTest {

    @Autowired SitePresentationSettingsRepository settings;
    @Autowired ActiveCvProfileRepository activeCvProfiles;
    @Autowired PersonalProfileRepository profiles;
    @Autowired ProjectRepository projects;
    @Autowired JdbcTemplate jdbc;

}
