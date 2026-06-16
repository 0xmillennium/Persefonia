package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.query.ContentTranslationSectionView;
import dev.persefonia.contentpublishing.application.query.TranslationCandidateItem;
import dev.persefonia.contentpublishing.application.service.TranslationGroupAdminQueryService;
import dev.persefonia.contentpublishing.application.service.TranslationGroupCommandService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryContentReadModels;
import dev.persefonia.contentpublishing.application.support.InMemorySeriesRepository;
import dev.persefonia.contentpublishing.application.support.InMemoryTranslationGroupRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranslationGroupAdminQueryServiceTest {
    private final InMemoryContentItemRepository contentItems = new InMemoryContentItemRepository();
    private final InMemoryTranslationGroupRepository groups = new InMemoryTranslationGroupRepository();
    private final TestContentAuthorizationPolicy authorization = new TestContentAuthorizationPolicy();
    private final TranslationGroupCommandService commands =
            new TranslationGroupCommandService(contentItems, groups, authorization);
    private final TranslationGroupAdminQueryService queries =
            new TranslationGroupAdminQueryService(
                    contentItems,
                    groups,
                    new InMemoryContentReadModels(contentItems, new InMemorySeriesRepository(), groups),
                    authorization);

    @Test
    void loadsCreateSectionWhenContentHasNoGroup() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE, "English");

        ContentTranslationSectionView section = queries.loadSection(OWNER, english.id());

        assertThat(section.hasGroup()).isFalse();
        assertThat(section.contentType()).isEqualTo(ContentType.ARTICLE);
        assertThat(section.candidates()).isEmpty();
    }

    @Test
    void loadsGroupDetailsWithEntryTitles() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE, "English title");
        createGroup(english);

        ContentTranslationSectionView section = queries.loadSection(OWNER, english.id());

        assertThat(section.hasGroup()).isTrue();
        assertThat(section.group().orElseThrow().entries()).singleElement().satisfies(entry -> {
            assertThat(entry.language()).isEqualTo(ContentLanguage.EN);
            assertThat(entry.title()).contains("English title");
        });
    }

    @Test
    void candidatesExcludeExistingLanguagesMembersAndOtherTypes() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE, "English");
        TranslationGroupId groupId = createGroup(english);

        ContentItem turkishArticle = content(ContentLanguage.TR, ContentType.ARTICLE, "Turkish article");
        content(ContentLanguage.EN, ContentType.ARTICLE, "Other English"); // excluded: language present
        content(ContentLanguage.TR, ContentType.NOTE, "Turkish note"); // excluded: different type
        ContentItem memberOfOtherGroup = content(ContentLanguage.TR, ContentType.ARTICLE, "Already grouped");
        createGroup(memberOfOtherGroup); // excluded: already in a group

        ContentTranslationSectionView section = queries.loadSection(OWNER, english.id());

        List<TranslationCandidateItem> candidates = section.candidates();
        assertThat(candidates).extracting(TranslationCandidateItem::contentItemId)
                .containsExactly(turkishArticle.id());
        assertThat(groupId).isNotNull();
    }

    @Test
    void nonOwnerCannotLoadSection() {
        ContentItem english = content(ContentLanguage.EN, ContentType.ARTICLE, "English");

        assertThatThrownBy(() -> queries.loadSection(EDITOR, english.id()))
                .isInstanceOf(SecurityException.class);
    }

    private ContentItem content(ContentLanguage language, ContentType type, String title) {
        ContentItem item = ContentItem.createDraft(
                ContentId.newId(), type, ContentVisibility.PUBLIC, language, NOW);
        item.changeTitle(Title.of(title), NOW);
        contentItems.add(item);
        return item;
    }

    private TranslationGroupId createGroup(ContentItem item) {
        return commands.create(new CreateTranslationGroupCommand(OWNER, item.id(), NOW)).translationGroupId();
    }
}
