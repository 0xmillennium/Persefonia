package dev.persefonia.contentpublishing.application;

import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.EDITOR;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.NOW;
import static dev.persefonia.contentpublishing.application.support.ContentApplicationFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;
import dev.persefonia.contentpublishing.application.discovery.ConfiguredContentCanonicalUrlFactory;
import dev.persefonia.contentpublishing.application.discovery.SeriesDiscoverabilityCoordinator;
import dev.persefonia.contentpublishing.application.discovery.SeriesDiscoveryProjectionFactory;
import dev.persefonia.contentpublishing.application.exception.SeriesCommandRejectedException;
import dev.persefonia.contentpublishing.application.service.SeriesCommandService;
import dev.persefonia.contentpublishing.application.support.InMemoryContentItemRepository;
import dev.persefonia.contentpublishing.application.support.InMemorySeriesRepository;
import dev.persefonia.contentpublishing.application.support.TestContentAuthorizationPolicy;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentItem;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.model.series.Series;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.discovery.application.contract.RoutePurpose;
import dev.persefonia.discovery.application.contract.SourceType;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionInput;
import dev.persefonia.discovery.application.projection.DiscoverableResourceProjectionResult;
import dev.persefonia.discovery.application.projection.RemoveDiscoverableResourceCommand;
import dev.persefonia.contentpublishing.domain.support.ContentItemTestFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeriesCommandServiceTest {
    private InMemoryContentItemRepository contentItems;
    private InMemorySeriesRepository seriesRepository;
    private List<DiscoverableResourceProjectionInput> projectionUpdates;
    private List<RemoveDiscoverableResourceCommand> projectionRemovals;
    private SeriesCommandService service;

    @BeforeEach
    void setUp() {
        contentItems = new InMemoryContentItemRepository();
        seriesRepository = new InMemorySeriesRepository();
        projectionUpdates = new ArrayList<>();
        projectionRemovals = new ArrayList<>();
        service = new SeriesCommandService(
                contentItems,
                seriesRepository,
                new TestContentAuthorizationPolicy(),
                new SeriesDiscoverabilityCoordinator(
                        input -> {
                            projectionUpdates.add(input);
                            return new DiscoverableResourceProjectionResult.Updated();
                        },
                        command -> {
                            projectionRemovals.add(command);
                            return new DiscoverableResourceProjectionResult.Removed();
                        },
                        new SeriesDiscoveryProjectionFactory(
                                new ConfiguredContentCanonicalUrlFactory("https://0xmillennium.dev"))));
    }

    @Test
    void ownerCanCreateSeries() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();

        assertThat(seriesRepository.findById(id)).isPresent();
    }

    @Test
    void creatingSeriesCreatesSeriesPageProjection() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();

        assertThat(projectionUpdates).singleElement().satisfies(input -> {
            assertThat(input.sourceType()).isEqualTo(SourceType.SERIES);
            assertThat(input.sourceEntityId().value()).isEqualTo(id.value());
            assertThat(input.routePurpose()).isEqualTo(RoutePurpose.SERIES_PAGE);
            assertThat(input.publicUrl().value()).isEqualTo("/en/series/path");
            assertThat(input.canonicalUrl().value()).isEqualTo("https://0xmillennium.dev/en/series/path");
        });
    }

    @Test
    void nonOwnerCannotCreateSeries() {
        assertThatThrownBy(() -> service.create(new CreateSeriesCommand(
                        EDITOR, ContentLanguage.EN, "Path", "path", null, NOW)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void duplicateSlugInSameLanguageRejected() {
        service.create(create("path", ContentLanguage.EN));

        assertThatThrownBy(() -> service.create(create("path", ContentLanguage.EN)))
                .isInstanceOf(SeriesCommandRejectedException.class)
                .extracting("reason")
                .isEqualTo(SeriesCommandRejectedException.Reason.DUPLICATE_SLUG);
    }

    @Test
    void sameSlugInDifferentLanguageAllowed() {
        service.create(create("path", ContentLanguage.EN));

        service.create(create("path", ContentLanguage.TR));

        assertThat(seriesRepository.findAllForAdmin()).hasSize(2);
    }

    @Test
    void ownerCanUpdateSeries() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();

        service.update(new UpdateSeriesCommand(OWNER, id, "Updated", "updated", "Description", NOW.plusSeconds(1)));

        assertThat(seriesRepository.findById(id).orElseThrow().slug().value()).isEqualTo("updated");
    }

    @Test
    void updatingSeriesSlugAndMetadataRefreshesSeriesPageProjection() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        projectionUpdates.clear();

        service.update(new UpdateSeriesCommand(OWNER, id, "Updated", "updated", "Description", NOW.plusSeconds(1)));

        assertThat(projectionUpdates).singleElement().satisfies(input -> {
            assertThat(input.sourceEntityId().value()).isEqualTo(id.value());
            assertThat(input.publicUrl().value()).isEqualTo("/en/series/updated");
            assertThat(input.title()).isEqualTo("Updated");
            assertThat(input.summary()).isEqualTo("Description");
            assertThat(input.searchText()).contains("Updated", "Description");
        });
    }

    @Test
    void ownerCanArchiveSeries() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();

        service.archive(new ArchiveSeriesCommand(OWNER, id, NOW.plusSeconds(1)));

        assertThat(seriesRepository.findById(id).orElseThrow().isArchived()).isTrue();
    }

    @Test
    void archivingSeriesRemovesSeriesPageProjection() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        projectionRemovals.clear();

        service.archive(new ArchiveSeriesCommand(OWNER, id, NOW.plusSeconds(1)));

        assertThat(projectionRemovals).singleElement().satisfies(command -> {
            assertThat(command.sourceType()).isEqualTo(SourceType.SERIES);
            assertThat(command.sourceEntityId().value()).isEqualTo(id.value());
        });
    }

    @Test
    void ownerCanAddEntryWithSameLanguage() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = content(ContentLanguage.EN);
        contentItems.add(item);

        service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1)));

        assertThat(seriesRepository.findById(id).orElseThrow().entries()).hasSize(1);
    }

    @Test
    void differentLanguageEntryRejected() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = content(ContentLanguage.TR);
        contentItems.add(item);

        assertThatThrownBy(() -> service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1))))
                .isInstanceOf(SeriesCommandRejectedException.class)
                .extracting("reason")
                .isEqualTo(SeriesCommandRejectedException.Reason.LANGUAGE_MISMATCH);
    }

    @Test
    void archivedContentCannotBeNewlyAdded() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        item.archive(NOW.plusSeconds(1));
        contentItems.add(item);

        assertThatThrownBy(() -> service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1))))
                .isInstanceOf(SeriesCommandRejectedException.class)
                .extracting("reason")
                .isEqualTo(SeriesCommandRejectedException.Reason.ARCHIVED_CONTENT);
    }

    @Test
    void draftContentCanBeAdded() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = content(ContentLanguage.EN);
        contentItems.add(item);

        service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1)));

        assertThat(seriesRepository.findById(id).orElseThrow().containsContentItem(item.id())).isTrue();
    }

    @Test
    void publishedContentCanBeAdded() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        contentItems.add(item);

        service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1)));

        assertThat(seriesRepository.findById(id).orElseThrow().containsContentItem(item.id())).isTrue();
    }

    @Test
    void unpublishedContentCanBeAdded() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = ContentItemTestFixtures.published(ContentVisibility.PUBLIC);
        item.unpublish(NOW.plusSeconds(1));
        contentItems.add(item);

        service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1)));

        assertThat(seriesRepository.findById(id).orElseThrow().containsContentItem(item.id())).isTrue();
    }

    @Test
    void duplicateEntryRejected() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = content(ContentLanguage.EN);
        contentItems.add(item);
        service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(2))))
                .isInstanceOf(SeriesCommandRejectedException.class)
                .extracting("reason")
                .isEqualTo(SeriesCommandRejectedException.Reason.DUPLICATE_ENTRY);
    }

    @Test
    void ownerCanRemoveEntry() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem item = content(ContentLanguage.EN);
        contentItems.add(item);
        service.addEntry(new AddSeriesEntryCommand(OWNER, id, item.id(), NOW.plusSeconds(1)));
        SeriesEntryId entryId = seriesRepository.findById(id).orElseThrow().entries().getFirst().id();

        service.removeEntry(new RemoveSeriesEntryCommand(OWNER, id, entryId, NOW.plusSeconds(2)));

        assertThat(seriesRepository.findById(id).orElseThrow().entries()).isEmpty();
    }

    @Test
    void ownerCanReorderEntries() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem first = content(ContentLanguage.EN);
        ContentItem second = content(ContentLanguage.EN);
        contentItems.add(first);
        contentItems.add(second);
        service.addEntry(new AddSeriesEntryCommand(OWNER, id, first.id(), NOW.plusSeconds(1)));
        service.addEntry(new AddSeriesEntryCommand(OWNER, id, second.id(), NOW.plusSeconds(2)));
        Series series = seriesRepository.findById(id).orElseThrow();
        SeriesEntryId firstEntry = series.entries().get(0).id();
        SeriesEntryId secondEntry = series.entries().get(1).id();

        service.reorderEntries(new ReorderSeriesEntriesCommand(OWNER, id, List.of(secondEntry, firstEntry), NOW.plusSeconds(3)));

        assertThat(seriesRepository.findById(id).orElseThrow().entries())
                .extracting(entry -> entry.id())
                .containsExactly(secondEntry, firstEntry);
    }

    @Test
    void entryReorderDoesNotCreateUnexpectedProjectionIfRouteIdentityUnchanged() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentItem first = content(ContentLanguage.EN);
        ContentItem second = content(ContentLanguage.EN);
        contentItems.add(first);
        contentItems.add(second);
        service.addEntry(new AddSeriesEntryCommand(OWNER, id, first.id(), NOW.plusSeconds(1)));
        service.addEntry(new AddSeriesEntryCommand(OWNER, id, second.id(), NOW.plusSeconds(2)));
        Series series = seriesRepository.findById(id).orElseThrow();
        projectionUpdates.clear();

        service.reorderEntries(new ReorderSeriesEntriesCommand(
                OWNER,
                id,
                List.of(series.entries().get(1).id(), series.entries().get(0).id()),
                NOW.plusSeconds(3)));

        assertThat(projectionUpdates).isEmpty();
    }

    @Test
    void nonOwnerCannotMutateEntries() {
        SeriesId id = service.create(create("path", ContentLanguage.EN)).seriesId();
        ContentId contentId = ContentId.newId();

        assertThatThrownBy(() -> service.addEntry(new AddSeriesEntryCommand(EDITOR, id, contentId, NOW.plusSeconds(1))))
                .isInstanceOf(SecurityException.class);
    }

    private static CreateSeriesCommand create(String slug, ContentLanguage language) {
        return new CreateSeriesCommand(OWNER, language, "Path", slug, null, NOW);
    }

    private static ContentItem content(ContentLanguage language) {
        return ContentItem.createDraft(ContentId.newId(), ContentType.ARTICLE, ContentVisibility.PUBLIC, language, NOW);
    }

}
