package dev.persefonia.app.platformoperations.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

class SpringBuildApplicationReleaseInfoAdapterTest {
    @Test
    void mapsOnlySafeApplicationNameAndSnapshotVersion() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        var properties = new java.util.Properties();
        properties.setProperty("name", "persefonia");
        properties.setProperty("version", "0.1.0-SNAPSHOT");
        properties.setProperty("time", "2026-09-04T00:00:00Z");
        when(provider.getIfAvailable()).thenReturn(new BuildProperties(properties));

        var info = new SpringBuildApplicationReleaseInfoAdapter(provider, new MockEnvironment()).releaseInfo();

        assertThat(info.applicationName()).isEqualTo("persefonia");
        assertThat(info.applicationVersion()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(info.toString()).doesNotContain("time", "path", "environment");
    }
}
