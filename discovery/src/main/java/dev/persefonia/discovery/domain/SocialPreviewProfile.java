package dev.persefonia.discovery.domain;

import java.util.UUID;

public record SocialPreviewProfile(
        OpenGraphTitle title,
        OpenGraphDescription description,
        UUID imageAssetId) {
    public static SocialPreviewProfile empty() {
        return new SocialPreviewProfile(null, null, null);
    }
}
