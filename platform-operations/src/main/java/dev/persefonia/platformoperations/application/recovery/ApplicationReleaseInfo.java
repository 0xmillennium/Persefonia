package dev.persefonia.platformoperations.application.recovery;

public record ApplicationReleaseInfo(String applicationName, String applicationVersion) {
    public ApplicationReleaseInfo {
        if (applicationName == null || applicationName.isBlank()) throw new IllegalArgumentException("application name required");
        if (applicationVersion == null || applicationVersion.isBlank()) throw new IllegalArgumentException("application version required");
    }
}
