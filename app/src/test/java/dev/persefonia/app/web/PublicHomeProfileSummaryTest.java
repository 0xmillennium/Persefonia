package dev.persefonia.app.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.web.PublicHomeTestConfiguration.PublicHomeProfileRepository;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.profileportfolio.domain.common.ExternalUrl;
import dev.persefonia.profileportfolio.domain.common.LinkLabel;
import dev.persefonia.profileportfolio.domain.common.SortOrder;
import dev.persefonia.profileportfolio.domain.profile.DisplayName;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLink;
import dev.persefonia.profileportfolio.domain.profile.ExternalProfileLinkId;
import dev.persefonia.profileportfolio.domain.profile.LocationText;
import dev.persefonia.profileportfolio.domain.profile.LongBio;
import dev.persefonia.profileportfolio.domain.profile.PersonalProfile;
import dev.persefonia.profileportfolio.domain.profile.ProfileId;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalization;
import dev.persefonia.profileportfolio.domain.profile.ProfileLocalizationId;
import dev.persefonia.profileportfolio.domain.profile.ShortBio;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(PublicHomeTestConfiguration.class)
@ActiveProfiles({"test", "public-home-mvc-test"})
class PublicHomeProfileSummaryTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicHomeProfileRepository profiles;

    @BeforeEach
    void reset() {
        profiles.reset();
    }

    @Test
    void homepageWithNoActiveProfileRendersSafely() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Profile short bio"))))
                .andExpect(content().string(not(containsString("Fake profile"))));
    }

    @Test
    void homepageWithActiveProfileInDefaultLanguageRendersSummaryAndValidatedLinks() throws Exception {
        profiles.setCurrent(profile(ContentLanguage.EN));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enes")))
                .andExpect(content().string(containsString("Profile short bio EN")))
                .andExpect(content().string(containsString("Istanbul")))
                .andExpect(content().string(containsString("href=\"https://example.test\"")))
                .andExpect(content().string(containsString("rel=\"noopener noreferrer\"")))
                .andExpect(content().string(not(containsString("Long profile bio"))))
                .andExpect(content().string(not(containsString("CV"))))
                .andExpect(content().string(not(containsString("Media"))))
                .andExpect(content().string(not(containsString("Project"))));
    }

    @Test
    void homepageMissingDefaultLanguageLocalizationDoesNotFallback() throws Exception {
        profiles.setCurrent(profile(ContentLanguage.TR));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Profile short bio TR"))))
                .andExpect(content().string(not(containsString("Enes"))));
    }

    private static PersonalProfile profile(ContentLanguage language) {
        return PersonalProfile.create(
                ProfileId.newId(),
                DisplayName.of("Enes"),
                true,
                List.of(new ProfileLocalization(
                        ProfileLocalizationId.newId(),
                        language,
                        ShortBio.of("Profile short bio " + language.name()),
                        LongBio.of("Long profile bio " + language.name()),
                        LocationText.of("Istanbul"),
                        List.of(),
                        List.of(),
                        List.of())),
                List.of(new ExternalProfileLink(
                        ExternalProfileLinkId.newId(),
                        LinkLabel.of("Website"),
                        ExternalUrl.of("https://example.test"),
                        SortOrder.of(1))),
                Instant.parse("2026-06-16T10:00:00Z"));
    }
}
