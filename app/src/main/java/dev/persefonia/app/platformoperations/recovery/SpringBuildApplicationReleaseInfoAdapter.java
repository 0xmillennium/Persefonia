package dev.persefonia.app.platformoperations.recovery;

import dev.persefonia.platformoperations.application.recovery.ApplicationReleaseInfo;
import dev.persefonia.platformoperations.application.recovery.ApplicationReleaseInfoQueryPort;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class SpringBuildApplicationReleaseInfoAdapter implements ApplicationReleaseInfoQueryPort {
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Environment environment;

    public SpringBuildApplicationReleaseInfoAdapter(
            ObjectProvider<BuildProperties> buildProperties, Environment environment) {
        this.buildProperties = Objects.requireNonNull(buildProperties, "buildProperties");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @Override
    public ApplicationReleaseInfo releaseInfo() {
        BuildProperties build = buildProperties.getIfAvailable();
        String name = environment.getProperty("spring.application.name", "persefonia");
        String version = build == null
                ? SpringBuildApplicationReleaseInfoAdapter.class.getPackage().getImplementationVersion()
                : build.getVersion();
        if (version == null || version.isBlank()) version = "0.1.0-SNAPSHOT";
        return new ApplicationReleaseInfo(name, version);
    }
}
