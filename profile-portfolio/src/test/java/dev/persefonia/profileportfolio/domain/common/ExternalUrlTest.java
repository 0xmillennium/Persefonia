package dev.persefonia.profileportfolio.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalUrlTest {
    @Test
    void acceptsHttpsUrl() {
        assertThat(ExternalUrl.of("https://example.com/path?Query=Yes").value())
                .isEqualTo("https://example.com/path?Query=Yes");
    }

    @Test
    void acceptsHttpUrl() {
        assertThat(ExternalUrl.of(" http://example.com "))
                .extracting(ExternalUrl::value)
                .isEqualTo("http://example.com");
    }

    @Test
    void rejectsJavascriptScheme() {
        assertThatThrownBy(() -> ExternalUrl.of("javascript:alert(1)"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsDataScheme() {
        assertThatThrownBy(() -> ExternalUrl.of("data:text/html,<script>alert(1)</script>"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsFileScheme() {
        assertThatThrownBy(() -> ExternalUrl.of("file:///etc/passwd"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsFtpScheme() {
        assertThatThrownBy(() -> ExternalUrl.of("ftp://example.com"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsSchemeRelativeUrl() {
        assertThatThrownBy(() -> ExternalUrl.of("//example.com"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsUrlWithoutScheme() {
        assertThatThrownBy(() -> ExternalUrl.of("example.com"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> ExternalUrl.of("https://"))
                .isInstanceOf(PortfolioValidationException.class);
        assertThatThrownBy(() -> ExternalUrl.of("http://"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsCrLfAndControlCharacters() {
        assertThatThrownBy(() -> ExternalUrl.of("https://example.com\r\nX-Test: yes"))
                .isInstanceOf(PortfolioValidationException.class);
        assertThatThrownBy(() -> ExternalUrl.of("https://example.com/\u0000"))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> ExternalUrl.of(" "))
                .isInstanceOf(PortfolioValidationException.class);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> ExternalUrl.of(null))
                .isInstanceOf(NullPointerException.class);
    }
}
