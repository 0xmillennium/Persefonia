package dev.persefonia.webpublic;

import java.util.List;

public record PublicHomeViewModel(
        String productName,
        String ownerAlias,
        String publicBaseUrl,
        String scriptPath,
        List<String> stylesheetPaths) {
    public PublicHomeViewModel {
        stylesheetPaths = List.copyOf(stylesheetPaths);
    }
}
