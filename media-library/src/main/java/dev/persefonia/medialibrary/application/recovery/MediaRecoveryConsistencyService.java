package dev.persefonia.medialibrary.application.recovery;

import dev.persefonia.medialibrary.application.storage.AssetStoragePort;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class MediaRecoveryConsistencyService {
    static final int PAGE_SIZE = 200;
    static final int ISSUE_LIMIT = 100;
    private static final int BUFFER_SIZE = 8192;

    private final MediaRecoveryInventoryReadPort inventory;
    private final AssetStoragePort storage;

    public MediaRecoveryConsistencyService(MediaRecoveryInventoryReadPort inventory, AssetStoragePort storage) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    public MediaRecoveryConsistencyReport verify() {
        Counts counts = new Counts();
        List<MediaRecoveryIssue> issues = new ArrayList<>(ISSUE_LIMIT);
        MediaRecoveryCursor cursor = null;
        do {
            MediaRecoveryInventoryPage page = inventory.readPage(cursor, PAGE_SIZE);
            for (MediaRecoveryObjectReference reference : page.items()) {
                verify(reference, counts, issues);
            }
            cursor = page.nextCursor();
        } while (cursor != null);
        return new MediaRecoveryConsistencyReport(
                counts.total, counts.verified, counts.unavailable, counts.sizeMismatch,
                counts.checksumMismatch, counts.issues(), issues, counts.issues() > issues.size());
    }

    private void verify(MediaRecoveryObjectReference reference, Counts counts, List<MediaRecoveryIssue> issues) {
        counts.total++;
        long bytes = 0;
        MessageDigest digest = sha256();
        try (InputStream input = storage.openStored(reference.storagePath())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                bytes += read;
                digest.update(buffer, 0, read);
            }
        } catch (RuntimeException | java.io.IOException failure) {
            counts.unavailable++;
            addIssue(issues, reference, MediaRecoveryIssueCategory.OBJECT_UNAVAILABLE);
            return;
        }
        if (bytes != reference.expectedSize()) {
            counts.sizeMismatch++;
            addIssue(issues, reference, MediaRecoveryIssueCategory.SIZE_MISMATCH);
        } else if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(reference.expectedChecksum())) {
            counts.checksumMismatch++;
            addIssue(issues, reference, MediaRecoveryIssueCategory.CHECKSUM_MISMATCH);
        } else {
            counts.verified++;
        }
    }

    private static void addIssue(List<MediaRecoveryIssue> issues, MediaRecoveryObjectReference reference,
            MediaRecoveryIssueCategory category) {
        if (issues.size() < ISSUE_LIMIT) {
            issues.add(new MediaRecoveryIssue(
                    category, reference.kind(), reference.assetId(), reference.variantName()));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class Counts {
        long total;
        long verified;
        long unavailable;
        long sizeMismatch;
        long checksumMismatch;
        long issues() { return unavailable + sizeMismatch + checksumMismatch; }
    }
}
