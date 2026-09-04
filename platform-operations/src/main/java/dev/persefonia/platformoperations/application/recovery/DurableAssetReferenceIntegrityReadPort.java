package dev.persefonia.platformoperations.application.recovery;

public interface DurableAssetReferenceIntegrityReadPort {
    DurableAssetReferenceIntegritySummary verify();
}
