package dev.persefonia.medialibrary.application.admin;

import dev.persefonia.medialibrary.application.asset.AssetRepository;
import dev.persefonia.medialibrary.application.authorization.MediaCommandAuthorizationPolicy;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommand;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetCommandService;
import dev.persefonia.medialibrary.application.processing.ProcessImageAssetResult;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommand;
import dev.persefonia.medialibrary.application.upload.UploadAssetCommandService;
import dev.persefonia.medialibrary.application.upload.UploadAssetResult;
import dev.persefonia.medialibrary.domain.asset.AltText;
import dev.persefonia.medialibrary.domain.asset.Asset;
import dev.persefonia.medialibrary.domain.asset.AssetKind;
import dev.persefonia.medialibrary.domain.asset.AssetVisibility;
import dev.persefonia.medialibrary.domain.asset.DecorativeImageFlag;
import dev.persefonia.medialibrary.domain.asset.ProcessingStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class MediaAdminCommandService implements MediaAdminCommandGateway {
    public static final String UPLOAD_COMMAND = "media.asset.upload";
    public static final String UPDATE_METADATA_COMMAND = "media.asset.update-metadata";

    private final MediaCommandAuthorizationPolicy authorization;
    private final UploadAssetCommandService uploads;
    private final ProcessImageAssetCommandService processing;
    private final AssetRepository assets;
    private final Clock clock;

    public MediaAdminCommandService(
            MediaCommandAuthorizationPolicy authorization,
            UploadAssetCommandService uploads,
            ProcessImageAssetCommandService processing,
            AssetRepository assets,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.processing = Objects.requireNonNull(processing, "processing");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AdminUploadAssetResult upload(AdminUploadAssetCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), UPLOAD_COMMAND);

        UploadAssetResult upload = uploads.upload(new UploadAssetCommand(
                command.originalFilename(),
                command.declaredContentType(),
                command.declaredExtension(),
                command.declaredSize(),
                command.byteSource()));

        if (upload instanceof UploadAssetResult.Rejected rejected) {
            return new AdminUploadAssetResult.Rejected(rejected.errors());
        }
        if (upload instanceof UploadAssetResult.Duplicate duplicate) {
            return new AdminUploadAssetResult.Duplicate(duplicate.existingAssetId());
        }

        UploadAssetResult.Created created = (UploadAssetResult.Created) upload;
        Asset asset = assets.findById(created.assetId()).orElseThrow(() ->
                new IllegalStateException("Created asset could not be loaded for processing."));
        if (asset.kind() != AssetKind.IMAGE) {
            return new AdminUploadAssetResult.Created(asset.id(), asset.processingStatus(), null);
        }

        ProcessImageAssetResult result = processing.process(new ProcessImageAssetCommand(asset.id()));
        Asset processed = assets.findById(asset.id()).orElse(asset);
        if (result instanceof ProcessImageAssetResult.Failed failed) {
            return new AdminUploadAssetResult.Created(
                    asset.id(), ProcessingStatus.FAILED, failed.reason());
        }
        return new AdminUploadAssetResult.Created(asset.id(), processed.processingStatus(), null);
    }

    @Override
    public AssetMetadataUpdateResult updateMetadata(UpdateAssetMetadataCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requireOwner(command.actor(), UPDATE_METADATA_COMMAND);

        var loaded = assets.findById(command.assetId());
        if (loaded.isEmpty()) {
            return new AssetMetadataUpdateResult.NotFound(command.assetId());
        }

        Asset asset = loaded.get();
        if (asset.kind() == AssetKind.DOCUMENT) {
            return rejected("visibility", "Document media cannot be published from this workflow.");
        }
        if (asset.kind() != AssetKind.IMAGE && hasImageMetadata(command)) {
            return rejected("altText", "Alt text and decorative flags apply to images only.");
        }

        Instant now = clock.instant();
        try {
            if (asset.kind() == AssetKind.IMAGE) {
                asset.updateAccessibility(altText(command.altText()), decorative(command.decorative()), now);
            }
            if (command.requestedVisibility() == AssetVisibility.PUBLIC) {
                asset.makePublic(now);
            } else {
                asset.makePrivate(now);
            }
            assets.save(asset);
            return new AssetMetadataUpdateResult.Updated(asset.id());
        } catch (IllegalArgumentException exception) {
            return rejected("visibility", userMessage(exception));
        }
    }

    private static boolean hasImageMetadata(UpdateAssetMetadataCommand command) {
        return command.decorative() || (command.altText() != null && !command.altText().isBlank());
    }

    private static AltText altText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AltText.of(value);
    }

    private static DecorativeImageFlag decorative(boolean decorative) {
        return decorative ? DecorativeImageFlag.decorative() : DecorativeImageFlag.informative();
    }

    private static AssetMetadataUpdateResult.Rejected rejected(String field, String message) {
        return new AssetMetadataUpdateResult.Rejected(List.of(new MediaAdminCommandError(field, message)));
    }

    private static String userMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "The requested media metadata is invalid."
                : message;
    }
}
