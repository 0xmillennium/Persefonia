package dev.persefonia.medialibrary.application.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.storage.*;
import dev.persefonia.medialibrary.domain.asset.AssetId;
import dev.persefonia.medialibrary.domain.asset.StoragePath;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;

class MediaRecoveryConsistencyServiceTest {
    @Test
    void streamsEveryPageAndDistinguishesUnavailableSizeAndSameSizeChecksumFailures() throws Exception {
        byte[] valid = "valid-object".getBytes(StandardCharsets.UTF_8);
        byte[] corruptSameSize = "other-object".getBytes(StandardCharsets.UTF_8);
        var validRef = reference("original/valid", valid, MediaRecoveryObjectKind.ORIGINAL, null);
        var missingRef = reference("original/missing", valid, MediaRecoveryObjectKind.ORIGINAL, null);
        var sizeRef = reference("variants/size", valid, MediaRecoveryObjectKind.VARIANT, "thumbnail");
        var checksumRef = reference("variants/checksum", valid, MediaRecoveryObjectKind.VARIANT, "medium");
        var inventory = new PagedInventory(List.of(validRef, missingRef, sizeRef, checksumRef), 2);
        var storage = new FakeStorage(Map.of(
                "original/valid", valid,
                "variants/size", new byte[] {1},
                "variants/checksum", corruptSameSize));

        MediaRecoveryConsistencyReport report = new MediaRecoveryConsistencyService(inventory, storage).verify();

        assertThat(report.totalObjects()).isEqualTo(4);
        assertThat(report.verifiedObjects()).isEqualTo(1);
        assertThat(report.unavailableObjects()).isEqualTo(1);
        assertThat(report.sizeMismatchObjects()).isEqualTo(1);
        assertThat(report.checksumMismatchObjects()).isEqualTo(1);
        assertThat(report.reportedIssues()).extracting(MediaRecoveryIssue::category)
                .containsExactly(
                        MediaRecoveryIssueCategory.OBJECT_UNAVAILABLE,
                        MediaRecoveryIssueCategory.SIZE_MISMATCH,
                        MediaRecoveryIssueCategory.CHECKSUM_MISMATCH);
        assertThat(inventory.reads).isEqualTo(2);
        assertThat(storage.opened).containsExactly(
                "original/valid", "original/missing", "variants/size", "variants/checksum");
    }

    @Test
    void issueDetailsAreCappedWhileAllObjectsAreScanned() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        List<MediaRecoveryObjectReference> references = new ArrayList<>();
        for (int index = 0; index < 125; index++) {
            references.add(reference("original/missing-" + index, expected, MediaRecoveryObjectKind.ORIGINAL, null));
        }
        var storage = new FakeStorage(Map.of());

        MediaRecoveryConsistencyReport report = new MediaRecoveryConsistencyService(
                new PagedInventory(references, 40), storage).verify();

        assertThat(report.issueCount()).isEqualTo(125);
        assertThat(report.reportedIssues()).hasSize(100);
        assertThat(report.reportedIssuesTruncated()).isTrue();
        assertThat(storage.opened).hasSize(125);
    }

    private static MediaRecoveryObjectReference reference(
            String path, byte[] expected, MediaRecoveryObjectKind kind, String variant) throws Exception {
        UUID objectId = UUID.randomUUID();
        return new MediaRecoveryObjectReference(kind, objectId, AssetId.newId(), variant,
                StoragePath.of(path), expected.length, HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(expected)));
    }

    private static final class PagedInventory implements MediaRecoveryInventoryReadPort {
        private final List<MediaRecoveryObjectReference> items;
        private final int pageSize;
        int reads;
        PagedInventory(List<MediaRecoveryObjectReference> items, int pageSize) {
            this.items = List.copyOf(items); this.pageSize = pageSize;
        }
        @Override public MediaRecoveryInventoryPage readPage(MediaRecoveryCursor after, int ignored) {
            int start = after == null ? 0 : (int) after.objectId().getLeastSignificantBits();
            int end = Math.min(items.size(), start + pageSize);
            reads++;
            MediaRecoveryCursor next = end < items.size()
                    ? new MediaRecoveryCursor(MediaRecoveryObjectKind.ORIGINAL, new UUID(0, end)) : null;
            return new MediaRecoveryInventoryPage(items.subList(start, end), next);
        }
    }

    private static final class FakeStorage implements AssetStoragePort {
        private final Map<String, byte[]> objects;
        private final List<String> opened = new ArrayList<>();
        FakeStorage(Map<String, byte[]> objects) { this.objects = objects; }
        @Override public InputStream openStored(StoragePath path) {
            opened.add(path.value());
            byte[] content = objects.get(path.value());
            if (content == null) throw new StorageWriteException("unavailable");
            return new NoBulkInputStream(content);
        }
        @Override public StagedAssetObject stageOriginal(OriginalAssetStagingRequest request) { throw unsupported(); }
        @Override public InputStream openStaged(StagedAssetObject stagedObject) { throw unsupported(); }
        @Override public StoredAssetObject commitStaged(StagedAssetObject stagedObject, FinalAssetStorageKey finalKey) { throw unsupported(); }
        @Override public StoredAssetObject storeVariant(VariantStorageRequest request) { throw unsupported(); }
        @Override public void deleteStagedIfExists(StagedAssetObject stagedObject) { throw unsupported(); }
        @Override public void deleteStoredIfExists(StoredAssetObject storedObject) { throw unsupported(); }
        @Override public void deleteStoredByPathIfExists(StoragePath storagePath) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }

    private static final class NoBulkInputStream extends ByteArrayInputStream {
        NoBulkInputStream(byte[] content) { super(content); }
        @Override public byte[] readAllBytes() {
            throw new AssertionError("recovery verification must stream through a bounded buffer");
        }
    }
}
