package dev.persefonia.medialibrary.application.recovery;

public interface MediaRecoveryInventoryReadPort {
    MediaRecoveryInventoryPage readPage(MediaRecoveryCursor after, int pageSize);
}
