package dev.persefonia.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.upload.UploadValidationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "spring.flyway.enabled=false",
                "management.health.redis.enabled=false"
        })
class MultipartConfigurationTest {
    @Autowired
    private MultipartProperties multipart;

    @Test
    void transportLimitsAcceptEveryDomainPermittedUpload() {
        long maxFileSize = multipart.getMaxFileSize().toBytes();
        long maxRequestSize = multipart.getMaxRequestSize().toBytes();

        assertThat(maxFileSize)
                .isEqualTo(UploadValidationPolicy.DEFAULT_MAX_IMAGE_BYTES)
                .isGreaterThanOrEqualTo(UploadValidationPolicy.DEFAULT_MAX_PDF_BYTES);
        assertThat(maxRequestSize).isEqualTo(11_534_336L).isGreaterThanOrEqualTo(maxFileSize);
    }
}
