package dev.persefonia.profileportfolio.application.exception;

public final class SitePresentationSettingsNotInitializedException
        extends SitePresentationSettingsApplicationException {
    public SitePresentationSettingsNotInitializedException() {
        super("Site presentation settings have not been initialized.");
    }
}
