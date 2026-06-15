package dev.persefonia.app.webpublic.tags;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.persefonia.app.webpublic.content.InMemoryPublicRouteResolver;
import dev.persefonia.app.webpublic.content.PublicContentTestConfiguration;
import dev.persefonia.app.webpublic.content.PublicContentTestItems;
import dev.persefonia.app.webpublic.content.PublicContentTestRepository;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.taxonomy.domain.model.NormalizedTagName;
import dev.persefonia.taxonomy.domain.model.Tag;
import dev.persefonia.taxonomy.domain.model.TagDescription;
import dev.persefonia.taxonomy.domain.model.TagName;
import dev.persefonia.taxonomy.domain.model.TagSlug;
import java.util.Set;
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
@Import({PublicContentTestConfiguration.class, PublicTagTestConfiguration.class})
@ActiveProfiles({"test", "public-content-mvc-test", "public-tag-mvc-test"})
class PublicTagPageControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired PublicTagTestRepository tags;
    @Autowired PublicContentTestRepository items;
    @Autowired InMemoryPublicRouteResolver routes;

    @BeforeEach
    void reset() {
        tags.reset();
        items.reset();
        routes.clear();
    }

    @Test
    void anonymousTagPageRendersEligibleContentNoindexCanonicalAndPublicCache() throws Exception {
        Tag tag = tag("Spring", "spring");
        tags.add(tag);
        routes.addTagFound("/en/tags/spring", tag.id().value());
        items.add(tagged(PublicContentTestItems.publishedPublic(
                dev.persefonia.contentpublishing.domain.content.ContentType.ARTICLE,
                dev.persefonia.contentpublishing.domain.content.ContentLanguage.EN,
                "articles",
                "listed"), tag));
        items.add(tagged(PublicContentTestItems.publishedUnlisted("unlisted"), tag));

        mockMvc.perform(get("/en/tags/spring"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(content().string(containsString("<meta name=\"robots\" content=\"noindex\">")))
                .andExpect(content().string(containsString(
                        "<link rel=\"canonical\" href=\"https://0xmillennium.dev/en/tags/spring\">")))
                .andExpect(content().string(containsString("Public &lt;Title&gt;")))
                .andExpect(content().string(containsString("/en/articles/listed")))
                .andExpect(content().string(not(containsString("/tr/articles/unlisted"))));
    }

    @Test
    void existingProjectedTagWithoutEligibleContentRendersEmptyState() throws Exception {
        Tag tag = tag("Empty", "empty");
        tags.add(tag);
        routes.addTagFound("/tr/tags/empty", tag.id().value());

        mockMvc.perform(get("/tr/tags/empty"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No public content is currently available for this tag.")));
    }

    @Test
    void missingProjectionMissingTagAndStaleProjectionReturnSafeNotFound() throws Exception {
        mockMvc.perform(get("/en/tags/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        routes.addTagFound("/en/tags/orphan", java.util.UUID.randomUUID());
        mockMvc.perform(get("/en/tags/orphan"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("source_entity_id"))));

        Tag current = tag("Current", "current");
        tags.add(current);
        routes.addTagFound("/en/tags/old", current.id().value());
        mockMvc.perform(get("/en/tags/old"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    @Test
    void tagIndexInvalidLanguageAndInvalidSlugAreNotPublic() throws Exception {
        mockMvc.perform(get("/en/tags")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/de/tags/spring")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/en/tags/Spring")).andExpect(status().is4xxClientError());
    }

    private static Tag tag(String name, String slug) {
        return Tag.create(
                dev.persefonia.taxonomy.domain.model.TagId.newId(),
                TagName.of(name),
                NormalizedTagName.ofCanonical(slug),
                TagSlug.ofCanonical(slug),
                TagDescription.ofNullable(name + " description"),
                PublicContentTestItems.NOW);
    }

    private static ContentItem tagged(ContentItem item, Tag tag) {
        item.replaceTags(Set.of(TagId.from(tag.id().value())), PublicContentTestItems.NOW.plusSeconds(5));
        return item;
    }
}
