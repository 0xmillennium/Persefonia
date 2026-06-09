package dev.persefonia.webpublic;

import java.util.List;

public interface FrontendAssetResolver {
    String scriptPath(String entry);

    List<String> stylesheetPaths(String entry);
}
