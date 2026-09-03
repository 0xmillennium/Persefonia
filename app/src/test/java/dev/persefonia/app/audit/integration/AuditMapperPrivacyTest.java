package dev.persefonia.app.audit.integration;

import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.change;
import static dev.persefonia.app.audit.integration.AdminAuditCommandFactory.metadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.persefonia.app.observability.CurrentRequestIdProvider;
import dev.persefonia.audit.application.command.AppendAuditChangeCommand;
import dev.persefonia.audit.application.command.AppendAuditMetadataCommand;
import dev.persefonia.audit.application.command.AppendAuditRecordCommand;
import dev.persefonia.audit.domain.record.AuditActorType;
import dev.persefonia.communication.application.authorization.ContactMessageCommandActor;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusCommand;
import dev.persefonia.communication.application.command.UpdateContactMessageStatusResult;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.contentpublishing.application.authorization.ContentCommandActor;
import dev.persefonia.contentpublishing.application.command.AddSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.AddTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.ArchiveSeriesCommand;
import dev.persefonia.contentpublishing.application.command.AssignContentTagsCommand;
import dev.persefonia.contentpublishing.application.command.ContentArchiveResult;
import dev.persefonia.contentpublishing.application.command.ContentDraftResult;
import dev.persefonia.contentpublishing.application.command.ContentFieldUpdate;
import dev.persefonia.contentpublishing.application.command.ContentPublishResult;
import dev.persefonia.contentpublishing.application.command.ContentUnpublishResult;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.CreateSeriesCommand;
import dev.persefonia.contentpublishing.application.command.CreateTranslationGroupCommand;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.RemoveSeriesEntryCommand;
import dev.persefonia.contentpublishing.application.command.RemoveTranslationEntryCommand;
import dev.persefonia.contentpublishing.application.command.ReorderSeriesEntriesCommand;
import dev.persefonia.contentpublishing.application.command.SeriesResult;
import dev.persefonia.contentpublishing.application.command.TranslationGroupResult;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.UpdateSeriesCommand;
import dev.persefonia.contentpublishing.domain.common.AdminIdentityRef;
import dev.persefonia.contentpublishing.domain.content.ContentId;
import dev.persefonia.contentpublishing.domain.content.ContentLanguage;
import dev.persefonia.contentpublishing.domain.content.ContentMetadata;
import dev.persefonia.contentpublishing.domain.content.ContentStatus;
import dev.persefonia.contentpublishing.domain.content.ContentType;
import dev.persefonia.contentpublishing.domain.content.ContentVisibility;
import dev.persefonia.contentpublishing.domain.content.MarkdownSource;
import dev.persefonia.contentpublishing.domain.content.Slug;
import dev.persefonia.contentpublishing.domain.content.Summary;
import dev.persefonia.contentpublishing.domain.content.TagId;
import dev.persefonia.contentpublishing.domain.content.Title;
import dev.persefonia.contentpublishing.domain.model.series.SeriesEntryId;
import dev.persefonia.contentpublishing.domain.model.series.SeriesId;
import dev.persefonia.contentpublishing.domain.revision.RevisionNumber;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupEntryId;
import dev.persefonia.contentpublishing.domain.translation.TranslationGroupId;
import dev.persefonia.discovery.application.authorization.AdminRedirectCommandActor;
import dev.persefonia.discovery.application.contract.PublicUrl;
import dev.persefonia.discovery.application.contract.RedirectReason;
import dev.persefonia.discovery.application.contract.RedirectStatusCode;
import dev.persefonia.discovery.application.redirect.CreateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.DeactivateManualRedirectCommand;
import dev.persefonia.discovery.application.redirect.RedirectRuleChangeSummary;
import dev.persefonia.discovery.domain.RedirectRuleId;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapOutcome;
import dev.persefonia.identityaccess.application.admin.bootstrap.AdminBootstrapResult;
import dev.persefonia.identityaccess.domain.admin.AdminAccount;
import dev.persefonia.identityaccess.domain.admin.AdminAccountId;
import dev.persefonia.identityaccess.domain.admin.AdminRole;
import dev.persefonia.identityaccess.domain.admin.DisplayName;
import dev.persefonia.identityaccess.domain.admin.EmailAddress;
import dev.persefonia.identityaccess.domain.admin.OidcSubject;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand;
import dev.persefonia.medialibrary.application.admin.AdminUploadAssetResult;
import dev.persefonia.medialibrary.application.admin.AssetMetadataUpdateResult;
import dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand;
import dev.persefonia.medialibrary.application.authorization.MediaCommandActor;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import dev.persefonia.profileportfolio.application.authorization.PortfolioCommandActor;
import dev.persefonia.profileportfolio.application.command.ActiveCvSelectionInput;
import dev.persefonia.profileportfolio.application.command.ActiveCvUpdateResult;
import dev.persefonia.profileportfolio.application.command.CreateProjectCommand;
import dev.persefonia.profileportfolio.application.command.ExternalProfileLinkInput;
import dev.persefonia.profileportfolio.application.command.PersonalProfileUpdateResult;
import dev.persefonia.profileportfolio.application.command.ProfileLocalizationInput;
import dev.persefonia.profileportfolio.application.command.ProjectLinkInput;
import dev.persefonia.profileportfolio.application.command.ProjectLocalizationInput;
import dev.persefonia.profileportfolio.application.command.ProjectMutationResult;
import dev.persefonia.profileportfolio.application.command.ProjectTechnologyInput;
import dev.persefonia.profileportfolio.application.command.SitePresentationSettingsUpdateResult;
import dev.persefonia.profileportfolio.application.command.UpdateActiveCvCommand;
import dev.persefonia.profileportfolio.application.command.UpdateProjectCommand;
import dev.persefonia.profileportfolio.application.command.UpdateSitePresentationSettingsCommand;
import dev.persefonia.profileportfolio.application.command.UpsertActivePersonalProfileCommand;
import dev.persefonia.taxonomy.application.authorization.TaxonomyCommandActor;
import dev.persefonia.taxonomy.application.command.ArchiveTagCommand;
import dev.persefonia.taxonomy.application.command.CreateTagCommand;
import dev.persefonia.taxonomy.application.command.TagCommandResult;
import dev.persefonia.taxonomy.application.command.UpdateTagCommand;
import dev.persefonia.taxonomy.domain.model.TagStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditMapperPrivacyTest {
    private static final Instant NOW = Instant.parse("2026-09-03T10:15:30Z");
    private static final UUID ACTOR_ID = UUID.fromString("7f31a9c2-0000-0000-0000-000000000001");
    private static final UUID ENTITY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final AdminAuditCommandFactory FACTORY = new AdminAuditCommandFactory(
            Clock.fixed(NOW, ZoneOffset.UTC), new CurrentRequestIdProvider());

    @Test
    void contentSeriesAndTranslationActionsUseOnlyAllowlistedValues() {
        var mapper = new ContentPublishingAuditMapper(FACTORY);
        ContentCommandActor actor = new ContentCommandActor(AdminIdentityRef.from(ACTOR_ID), true, true);
        ContentId contentId = ContentId.from(ENTITY_ID);
        ContentDraftResult draft = mock(ContentDraftResult.class);
        when(draft.contentId()).thenReturn(contentId);
        when(draft.status()).thenReturn(ContentStatus.DRAFT);
        when(draft.visibility()).thenReturn(ContentVisibility.PUBLIC);
        when(draft.language()).thenReturn(ContentLanguage.EN);
        ContentDraftResult updatedDraft = mock(ContentDraftResult.class);
        when(updatedDraft.contentId()).thenReturn(contentId);
        when(updatedDraft.visibility()).thenReturn(ContentVisibility.PRIVATE);

        var create = new CreateContentDraftCommand(
                actor, ContentType.ARTICLE, ContentVisibility.PUBLIC, ContentLanguage.EN, NOW);
        var update = new UpdateContentDraftCommand(
                actor,
                contentId,
                ContentFieldUpdate.set(Slug.of("private-slug")),
                ContentFieldUpdate.set(Title.of("VERY_PRIVATE_TITLE_VALUE")),
                ContentFieldUpdate.set(Summary.of("VERY_PRIVATE_SUMMARY_VALUE")),
                ContentFieldUpdate.set(MarkdownSource.of("VERY_PRIVATE_MARKDOWN_VALUE")),
                ContentFieldUpdate.set(mock(ContentMetadata.class)),
                ContentFieldUpdate.set(ContentVisibility.PRIVATE),
                NOW);
        ContentPublishResult published = mock(ContentPublishResult.class);
        when(published.contentId()).thenReturn(contentId);
        when(published.status()).thenReturn(ContentStatus.PUBLISHED);
        when(published.revisionNumber()).thenReturn(RevisionNumber.of(4));
        ContentUnpublishResult unpublished =
                new ContentUnpublishResult(contentId, ContentStatus.UNPUBLISHED, NOW);
        ContentArchiveResult archived = new ContentArchiveResult(contentId, ContentStatus.ARCHIVED, NOW);
        var publish = new PublishContentCommand(actor, contentId, NOW, null);
        var unpublish = new UnpublishContentCommand(actor, contentId, NOW);
        var archive = new ArchiveContentCommand(actor, contentId, NOW);
        var tags = new AssignContentTagsCommand(
                actor,
                contentId,
                List.of(TagId.from(UUID.randomUUID()), TagId.from(UUID.randomUUID())),
                NOW);

        SeriesId seriesId = SeriesId.from(ENTITY_ID);
        SeriesResult series = new SeriesResult(seriesId);
        var seriesCreate = new CreateSeriesCommand(
                actor, ContentLanguage.EN, "VERY_PRIVATE_SERIES_TITLE", "private-series", "private", NOW);
        var seriesUpdate = new UpdateSeriesCommand(
                actor, seriesId, "VERY_PRIVATE_SERIES_TITLE", "private-series", "private", NOW);
        var seriesArchive = new ArchiveSeriesCommand(actor, seriesId, NOW);
        var seriesAdd = new AddSeriesEntryCommand(actor, seriesId, contentId, NOW);
        var seriesRemove = new RemoveSeriesEntryCommand(actor, seriesId, SeriesEntryId.newId(), NOW);
        var seriesReorder = new ReorderSeriesEntriesCommand(
                actor, seriesId, List.of(SeriesEntryId.newId(), SeriesEntryId.newId()), NOW);
        SeriesResult removedSeries = new SeriesResult(seriesId, contentId);

        TranslationGroupId groupId = TranslationGroupId.from(ENTITY_ID);
        TranslationGroupResult group = new TranslationGroupResult(groupId);
        TranslationGroupResult removedGroup = new TranslationGroupResult(groupId, contentId);
        var groupCreate = new CreateTranslationGroupCommand(actor, contentId, NOW);
        var groupAdd = new AddTranslationEntryCommand(actor, groupId, contentId, NOW);
        var groupRemove = new RemoveTranslationEntryCommand(actor, groupId, TranslationGroupEntryId.newId(), NOW);

        List<AppendAuditRecordCommand> mapped = List.of(
                mapper.draftCreated(create, draft),
                mapper.draftUpdated(update, updatedDraft),
                mapper.published(publish, published),
                mapper.unpublished(unpublish, unpublished),
                mapper.archived(archive, archived),
                mapper.tagsReplaced(tags),
                mapper.seriesCreated(seriesCreate, series),
                mapper.seriesUpdated(seriesUpdate, series),
                mapper.seriesArchived(seriesArchive, series),
                mapper.seriesEntryAdded(seriesAdd, series),
                mapper.seriesEntryRemoved(seriesRemove, removedSeries),
                mapper.seriesEntriesReordered(seriesReorder, series),
                mapper.translationGroupCreated(groupCreate, group),
                mapper.translationGroupEntryAdded(groupAdd, group),
                mapper.translationGroupEntryRemoved(groupRemove, removedGroup));

        assertThat(mapped).extracting(AppendAuditRecordCommand::action).containsExactly(
                AuditActionCatalog.CONTENT_DRAFT_CREATED,
                AuditActionCatalog.CONTENT_DRAFT_UPDATED,
                AuditActionCatalog.CONTENT_PUBLISHED,
                AuditActionCatalog.CONTENT_UNPUBLISHED,
                AuditActionCatalog.CONTENT_ARCHIVED,
                AuditActionCatalog.CONTENT_TAGS_REPLACED,
                AuditActionCatalog.SERIES_CREATED,
                AuditActionCatalog.SERIES_UPDATED,
                AuditActionCatalog.SERIES_ARCHIVED,
                AuditActionCatalog.SERIES_ENTRY_ADDED,
                AuditActionCatalog.SERIES_ENTRY_REMOVED,
                AuditActionCatalog.SERIES_ENTRIES_REORDERED,
                AuditActionCatalog.TRANSLATION_GROUP_CREATED,
                AuditActionCatalog.TRANSLATION_GROUP_ENTRY_ADDED,
                AuditActionCatalog.TRANSLATION_GROUP_ENTRY_REMOVED);
        assertAdminCommand(mapped.get(0), AuditActionCatalog.CONTENT_DRAFT_CREATED,
                AuditEntityCatalog.CONTENT_ITEM, List.of(), List.of(
                        metadata("content_type", ContentType.ARTICLE),
                        metadata("status", ContentStatus.DRAFT),
                        metadata("visibility", ContentVisibility.PUBLIC),
                        metadata("language", ContentLanguage.EN)));
        assertAdminCommand(mapped.get(1), AuditActionCatalog.CONTENT_DRAFT_UPDATED,
                AuditEntityCatalog.CONTENT_ITEM, List.of(), List.of(
                        metadata("slug_changed", true),
                        metadata("title_changed", true),
                        metadata("summary_changed", true),
                        metadata("markdown_changed", true),
                        metadata("metadata_changed", true),
                        metadata("visibility_changed", true),
                        metadata("visibility", ContentVisibility.PRIVATE)));
        assertAdminCommand(mapped.get(2), AuditActionCatalog.CONTENT_PUBLISHED,
                AuditEntityCatalog.CONTENT_ITEM, List.of(), List.of(
                        metadata("status", ContentStatus.PUBLISHED), metadata("revision_number", 4)));
        assertAdminCommand(mapped.get(3), AuditActionCatalog.CONTENT_UNPUBLISHED,
                AuditEntityCatalog.CONTENT_ITEM, List.of(), List.of(metadata("status", ContentStatus.UNPUBLISHED)));
        assertAdminCommand(mapped.get(4), AuditActionCatalog.CONTENT_ARCHIVED,
                AuditEntityCatalog.CONTENT_ITEM, List.of(), List.of(metadata("status", ContentStatus.ARCHIVED)));
        assertAdminCommand(mapped.get(5), AuditActionCatalog.CONTENT_TAGS_REPLACED,
                AuditEntityCatalog.CONTENT_ITEM, List.of(), List.of(metadata("tag_count", 2)));
        assertAdminCommand(mapped.get(6), AuditActionCatalog.SERIES_CREATED,
                AuditEntityCatalog.SERIES, List.of(), List.of(metadata("language", ContentLanguage.EN)));
        assertAdminCommand(mapped.get(7), AuditActionCatalog.SERIES_UPDATED,
                AuditEntityCatalog.SERIES, List.of(), List.of());
        assertAdminCommand(mapped.get(8), AuditActionCatalog.SERIES_ARCHIVED,
                AuditEntityCatalog.SERIES, List.of(), List.of(metadata("status", "ARCHIVED")));
        assertAdminCommand(mapped.get(9), AuditActionCatalog.SERIES_ENTRY_ADDED,
                AuditEntityCatalog.SERIES, List.of(), List.of(metadata("content_item_id", contentId.value())));
        assertAdminCommand(mapped.get(10), AuditActionCatalog.SERIES_ENTRY_REMOVED,
                AuditEntityCatalog.SERIES, List.of(), List.of(metadata("content_item_id", contentId.value())));
        assertAdminCommand(mapped.get(11), AuditActionCatalog.SERIES_ENTRIES_REORDERED,
                AuditEntityCatalog.SERIES, List.of(), List.of(metadata("entry_count", 2)));
        assertAdminCommand(mapped.get(12), AuditActionCatalog.TRANSLATION_GROUP_CREATED,
                AuditEntityCatalog.TRANSLATION_GROUP, List.of(),
                List.of(metadata("initial_content_item_id", contentId.value())));
        assertAdminCommand(mapped.get(13), AuditActionCatalog.TRANSLATION_GROUP_ENTRY_ADDED,
                AuditEntityCatalog.TRANSLATION_GROUP, List.of(),
                List.of(metadata("content_item_id", contentId.value())));
        assertAdminCommand(mapped.get(14), AuditActionCatalog.TRANSLATION_GROUP_ENTRY_REMOVED,
                AuditEntityCatalog.TRANSLATION_GROUP, List.of(),
                List.of(metadata("content_item_id", contentId.value())));
        assertThat(mapped.toString())
                .doesNotContain("VERY_PRIVATE_TITLE_VALUE")
                .doesNotContain("VERY_PRIVATE_SUMMARY_VALUE")
                .doesNotContain("VERY_PRIVATE_MARKDOWN_VALUE")
                .doesNotContain("VERY_PRIVATE_SERIES_TITLE")
                .doesNotContain("private-slug")
                .doesNotContain("private-series");
    }

    @Test
    void taxonomyActionsExcludeAuthoredTagValues() {
        var mapper = new TaxonomyAuditMapper(FACTORY);
        var actor = new TaxonomyCommandActor(ACTOR_ID, true, true);
        var tagId = dev.persefonia.taxonomy.domain.model.TagId.from(ENTITY_ID);
        var result = new TagCommandResult(tagId, TagStatus.ACTIVE, NOW);
        List<AppendAuditRecordCommand> mapped = List.of(
                mapper.created(new CreateTagCommand(actor, "PRIVATE_TAG_NAME", "private-tag", "private", NOW), result),
                mapper.updated(new UpdateTagCommand(actor, tagId, "PRIVATE_TAG_NAME", "private-tag", "private", NOW), result),
                mapper.archived(new ArchiveTagCommand(actor, tagId, NOW),
                        new TagCommandResult(tagId, TagStatus.ARCHIVED, NOW)));

        assertThat(mapped).extracting(AppendAuditRecordCommand::action).containsExactly(
                AuditActionCatalog.TAG_CREATED, AuditActionCatalog.TAG_UPDATED, AuditActionCatalog.TAG_ARCHIVED);
        assertAdminCommand(mapped.get(0), AuditActionCatalog.TAG_CREATED,
                AuditEntityCatalog.TAG, List.of(), List.of(metadata("status", TagStatus.ACTIVE)));
        assertAdminCommand(mapped.get(1), AuditActionCatalog.TAG_UPDATED,
                AuditEntityCatalog.TAG, List.of(), List.of(metadata("status", TagStatus.ACTIVE)));
        assertAdminCommand(mapped.get(2), AuditActionCatalog.TAG_ARCHIVED,
                AuditEntityCatalog.TAG, List.of(), List.of(metadata("status", TagStatus.ARCHIVED)));
        assertThat(mapped.toString()).doesNotContain("PRIVATE_TAG_NAME", "private-tag");
    }

    @Test
    void portfolioActionsExcludeAuthoredTextAndUrls() {
        var mapper = new ProfilePortfolioAuditMapper(FACTORY);
        var actor = new PortfolioCommandActor(ACTOR_ID, true, true);
        var localization = new ProjectLocalizationInput(
                "EN", "private-project", "Private title", "VERY_PRIVATE_PROJECT_SUMMARY", List.of());
        var create = new CreateProjectCommand(
                actor,
                "ACTIVE",
                "PUBLIC",
                true,
                1,
                Set.of(UUID.randomUUID()),
                List.of(localization),
                List.of(new ProjectTechnologyInput("PRIVATE_TECH", "LANGUAGE", 1)),
                List.of(new ProjectLinkInput("Private", "https://private.example/path", "SOURCE", 1)),
                NOW);
        var update = new UpdateProjectCommand(
                actor, ENTITY_ID, "ACTIVE", "PUBLIC", true, 1, create.tagIds(), create.localizations(),
                create.technologies(), create.links(), NOW);
        var projectResult = new ProjectMutationResult(ENTITY_ID, true, NOW, 1);
        var profile = new UpsertActivePersonalProfileCommand(
                actor,
                "PRIVATE_DISPLAY_NAME",
                List.of(new ProfileLocalizationInput(
                        "EN", "VERY_PRIVATE_BIO", "VERY_PRIVATE_LONG_BIO", "PRIVATE_LOCATION",
                        List.of(), List.of(), List.of())),
                List.of(new ExternalProfileLinkInput("Private", "https://private.example/profile", 1)),
                NOW);
        var settings = new UpdateSitePresentationSettingsCommand(
                actor, "PRIVATE_SITE_NAME", "EN", Set.of("EN", "TR"), "PRIVATE_SUFFIX",
                "PRIVATE_META_DESCRIPTION", "SYSTEM", true, false, true, 4, 5, NOW);
        var cv = new UpdateActiveCvCommand(
                actor, List.of(new ActiveCvSelectionInput("EN", UUID.randomUUID().toString(), "PRIVATE_CV_LABEL")), NOW);

        List<AppendAuditRecordCommand> mapped = List.of(
                mapper.projectCreated(create, projectResult),
                mapper.projectUpdated(update, projectResult),
                mapper.profileUpserted(profile, new PersonalProfileUpdateResult(ENTITY_ID, true, NOW, 1)),
                mapper.siteSettingsUpdated(settings, new SitePresentationSettingsUpdateResult(ENTITY_ID, NOW, 1)),
                mapper.activeCvUpdated(cv, new ActiveCvUpdateResult(true, ENTITY_ID, NOW, 1, List.of())));

        assertThat(mapped).extracting(AppendAuditRecordCommand::action).containsExactly(
                AuditActionCatalog.PROJECT_CREATED,
                AuditActionCatalog.PROJECT_UPDATED,
                AuditActionCatalog.PROFILE_UPSERTED,
                AuditActionCatalog.SITE_SETTINGS_UPDATED,
                AuditActionCatalog.CV_ACTIVE_UPDATED);
        List<AppendAuditMetadataCommand> projectMetadata = List.of(
                metadata("status", "ACTIVE"), metadata("visibility", "PUBLIC"), metadata("featured", true),
                metadata("tag_count", 1), metadata("localization_count", 1), metadata("technology_count", 1),
                metadata("link_count", 1));
        assertAdminCommand(mapped.get(0), AuditActionCatalog.PROJECT_CREATED,
                AuditEntityCatalog.PROJECT, List.of(), projectMetadata);
        assertAdminCommand(mapped.get(1), AuditActionCatalog.PROJECT_UPDATED,
                AuditEntityCatalog.PROJECT, List.of(), projectMetadata);
        assertAdminCommand(mapped.get(2), AuditActionCatalog.PROFILE_UPSERTED,
                AuditEntityCatalog.PERSONAL_PROFILE, List.of(), List.of(
                        metadata("created", true), metadata("localization_count", 1),
                        metadata("external_link_count", 1)));
        assertAdminCommand(mapped.get(3), AuditActionCatalog.SITE_SETTINGS_UPDATED,
                AuditEntityCatalog.SITE_PRESENTATION_SETTINGS, List.of(), List.of(
                        metadata("default_language", "EN"),
                        metadata("supported_language_count", 2),
                        metadata("default_theme", "SYSTEM"),
                        metadata("show_featured_projects", true),
                        metadata("show_latest_writing", false),
                        metadata("show_research_highlights", true),
                        metadata("featured_project_limit", 4),
                        metadata("latest_writing_limit", 5)));
        assertAdminCommand(mapped.get(4), AuditActionCatalog.CV_ACTIVE_UPDATED,
                AuditEntityCatalog.ACTIVE_CV_PROFILE, List.of(), List.of(metadata("selection_count", 1)));
        assertThat(mapped.toString())
                .doesNotContain("VERY_PRIVATE_PROJECT_SUMMARY")
                .doesNotContain("https://private.example/path")
                .doesNotContain("VERY_PRIVATE_BIO")
                .doesNotContain("VERY_PRIVATE_LONG_BIO")
                .doesNotContain("PRIVATE_DISPLAY_NAME")
                .doesNotContain("PRIVATE_SITE_NAME")
                .doesNotContain("PRIVATE_META_DESCRIPTION")
                .doesNotContain("PRIVATE_CV_LABEL");
    }

    @Test
    void mediaActionsExcludeFilenameAndAltText() {
        var mapper = new MediaAuditMapper(FACTORY);
        var actor = new MediaCommandActor(ACTOR_ID, true, true);
        AdminUploadAssetCommand upload = mock(AdminUploadAssetCommand.class);
        when(upload.actor()).thenReturn(actor);
        when(upload.originalFilename()).thenReturn("VERY_PRIVATE_FILENAME.pdf");
        var update = new UpdateAssetMetadataCommand(
                actor, AssetId.from(ENTITY_ID), AssetVisibility.PUBLIC, "VERY_PRIVATE_ALT_TEXT", true);
        List<AppendAuditRecordCommand> mapped = List.of(
                mapper.uploaded(upload, new AdminUploadAssetResult.Created(
                        AssetId.from(ENTITY_ID), ProcessingStatus.PROCESSED, "PRIVATE_WARNING")),
                mapper.metadataUpdated(update, new AssetMetadataUpdateResult.Updated(AssetId.from(ENTITY_ID))));

        assertThat(mapped).extracting(AppendAuditRecordCommand::action)
                .containsExactly(AuditActionCatalog.ASSET_UPLOADED, AuditActionCatalog.ASSET_METADATA_UPDATED);
        assertAdminCommand(mapped.get(0), AuditActionCatalog.ASSET_UPLOADED,
                AuditEntityCatalog.ASSET, List.of(), List.of(metadata("processing_status", ProcessingStatus.PROCESSED)));
        assertAdminCommand(mapped.get(1), AuditActionCatalog.ASSET_METADATA_UPDATED,
                AuditEntityCatalog.ASSET, List.of(), List.of(
                        metadata("visibility", AssetVisibility.PUBLIC), metadata("decorative", true)));
        assertThat(mapped.toString())
                .doesNotContain("VERY_PRIVATE_FILENAME.pdf", "VERY_PRIVATE_ALT_TEXT", "PRIVATE_WARNING");
    }

    @Test
    void redirectAndContactActionsHaveExactSafePayloads() {
        var redirectMapper = new DiscoveryAuditMapper(FACTORY);
        var redirectActor = new AdminRedirectCommandActor(ACTOR_ID, true, true);
        var redirectId = new RedirectRuleId(ENTITY_ID);
        var summary = new RedirectRuleChangeSummary(
                redirectId,
                new PublicUrl("/writing/old-slug"),
                new PublicUrl("/projects/persefonia"),
                RedirectStatusCode.MOVED_PERMANENTLY_301,
                RedirectReason.MANUAL);
        var create = new CreateManualRedirectCommand(
                redirectActor, summary.sourceUrl(), summary.targetUrl(), summary.statusCode());
        var deactivate = new DeactivateManualRedirectCommand(redirectActor, redirectId);
        AppendAuditRecordCommand created = redirectMapper.created(create, summary);
        AppendAuditRecordCommand deactivated = redirectMapper.deactivated(deactivate, summary);

        var contactMapper = new CommunicationAuditMapper(FACTORY);
        var contactActor = new ContactMessageCommandActor(ACTOR_ID, true, true);
        var messageId = ContactMessageId.from(ENTITY_ID);
        var contactCommand = new UpdateContactMessageStatusCommand(
                contactActor, messageId, ContactMessageStatus.READ, NOW);
        AppendAuditRecordCommand contact = contactMapper.statusChanged(
                contactCommand,
                new UpdateContactMessageStatusResult.Updated(
                        messageId, ContactMessageStatus.NEW, ContactMessageStatus.READ));

        assertThat(List.of(created, deactivated, contact)).extracting(AppendAuditRecordCommand::action)
                .containsExactly(
                        AuditActionCatalog.REDIRECT_CREATED,
                        AuditActionCatalog.REDIRECT_DEACTIVATED,
                        AuditActionCatalog.CONTACT_MESSAGE_STATUS_CHANGED);
        List<AppendAuditMetadataCommand> redirectMetadata = List.of(
                metadata("source_path", "/writing/old-slug"),
                metadata("target_path", "/projects/persefonia"),
                metadata("status_code", 301),
                metadata("reason", RedirectReason.MANUAL));
        assertAdminCommand(created, AuditActionCatalog.REDIRECT_CREATED,
                AuditEntityCatalog.REDIRECT_RULE, List.of(), redirectMetadata);
        assertAdminCommand(deactivated, AuditActionCatalog.REDIRECT_DEACTIVATED,
                AuditEntityCatalog.REDIRECT_RULE, List.of(), redirectMetadata);
        assertAdminCommand(contact, AuditActionCatalog.CONTACT_MESSAGE_STATUS_CHANGED,
                AuditEntityCatalog.CONTACT_MESSAGE, List.of(change("status", "NEW", "READ")), List.of());
        assertThat(contact.toString())
                .doesNotContain("VERY_PRIVATE_CONTACT_BODY", "owner@example.test");
    }

    @Test
    void bootstrapActionExcludesAllExternalIdentityData() {
        var mapper = new IdentityAccessAuditMapper(FACTORY);
        AdminAccount account = AdminAccount.create(
                AdminAccountId.of(ENTITY_ID),
                OidcSubject.of("VERY_PRIVATE_OIDC_SUBJECT"),
                EmailAddress.of("owner@example.test"),
                DisplayName.of("VERY_PRIVATE_EXTERNAL_DISPLAY"),
                Set.of(AdminRole.OWNER),
                NOW);
        AppendAuditRecordCommand command = mapper.bootstrapped(new AdminBootstrapResult(
                account, AdminBootstrapOutcome.INITIAL_OWNER_BOOTSTRAPPED));

        assertThat(command.action()).isEqualTo(AuditActionCatalog.ADMIN_ACCOUNT_BOOTSTRAPPED);
        assertThat(command.actorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(command.actorContext()).isNull();
        assertThat(command.actorSourceType()).isNull();
        assertThat(command.actorId()).isNull();
        assertThat(command.actorDisplay()).isEqualTo("Identity bootstrap");
        assertThat(command.entityContext()).isEqualTo("iam");
        assertThat(command.entityType()).isEqualTo("admin_account");
        assertThat(command.entityId()).isEqualTo(ENTITY_ID);
        assertThat(command.requestId()).isNull();
        assertThat(command.occurredAt()).isEqualTo(NOW);
        assertThat(command.changes()).isEmpty();
        assertThat(command.metadata()).containsExactly(
                metadata("bootstrap_outcome", AdminBootstrapOutcome.INITIAL_OWNER_BOOTSTRAPPED));
        assertThat(command.toString())
                .doesNotContain("VERY_PRIVATE_OIDC_SUBJECT", "owner@example.test", "VERY_PRIVATE_EXTERNAL_DISPLAY");
    }

    private static void assertAdminCommand(
            AppendAuditRecordCommand command,
            String action,
            AuditEntityCatalog.Entity entity,
            List<AppendAuditChangeCommand> changes,
            List<AppendAuditMetadataCommand> metadata) {
        assertThat(command.action()).isEqualTo(action);
        assertThat(command.actorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(command.actorContext()).isEqualTo("iam");
        assertThat(command.actorSourceType()).isEqualTo("admin_account");
        assertThat(command.actorId()).isEqualTo(ACTOR_ID);
        assertThat(command.actorDisplay()).isEqualTo("Admin 7f31a9c2");
        assertThat(command.entityContext()).isEqualTo(entity.context());
        assertThat(command.entityType()).isEqualTo(entity.type());
        assertThat(command.entityId()).isEqualTo(ENTITY_ID);
        assertThat(command.requestId()).isNull();
        assertThat(command.occurredAt()).isEqualTo(NOW);
        assertThat(command.changes()).containsExactlyElementsOf(changes);
        assertThat(command.metadata()).containsExactlyElementsOf(metadata);
    }
}
