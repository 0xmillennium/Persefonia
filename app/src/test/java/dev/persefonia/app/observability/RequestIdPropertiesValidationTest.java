package dev.persefonia.app.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RequestIdPropertiesValidationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(ObservabilityConfiguration.class);

    @Test
    void validDefaultsAreAccepted() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            RequestIdProperties properties = context.getBean(RequestIdProperties.class);
            assertThat(properties.getIncomingHeader()).isEqualTo("X-Request-Id");
            assertThat(properties.getResponseHeader()).isEqualTo("X-Request-Id");
            assertThat(properties.getMaxLength()).isEqualTo(80);
        });
    }

    @Test
    void blankIncomingHeaderIsRejected() {
        assertInvalid("persefonia.observability.request-id.incoming-header= ");
    }

    @Test
    void blankResponseHeaderIsRejected() {
        assertInvalid("persefonia.observability.request-id.response-header= ");
    }

    @Test
    void headerContainingCrLfIsRejected() {
        assertInvalid("persefonia.observability.request-id.incoming-header=X-Request-Id\\r\\nInjected");
    }

    @Test
    void headerContainingControlCharacterIsRejected() {
        assertInvalid("persefonia.observability.request-id.response-header=X-Request-Id\u007f");
    }

    @Test
    void maxLengthZeroIsRejected() {
        assertInvalid("persefonia.observability.request-id.max-length=0");
    }

    @Test
    void maxLengthTooSmallIsRejected() {
        assertInvalid("persefonia.observability.request-id.max-length=15");
    }

    @Test
    void maxLengthTooLargeIsRejected() {
        assertInvalid("persefonia.observability.request-id.max-length=129");
    }

    @Test
    void validCustomHeadersAreAccepted() {
        contextRunner
                .withPropertyValues(
                        "persefonia.observability.request-id.incoming-header=X-Correlation_Id",
                        "persefonia.observability.request-id.response-header=X-Trace.Id",
                        "persefonia.observability.request-id.max-length=128")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RequestIdProperties properties = context.getBean(RequestIdProperties.class);
                    assertThat(properties.getIncomingHeader()).isEqualTo("X-Correlation_Id");
                    assertThat(properties.getResponseHeader()).isEqualTo("X-Trace.Id");
                    assertThat(properties.getMaxLength()).isEqualTo(128);
                });
    }

    private void assertInvalid(String property) {
        contextRunner
                .withPropertyValues(property)
                .run(context -> assertThat(context).hasFailed());
    }
}
