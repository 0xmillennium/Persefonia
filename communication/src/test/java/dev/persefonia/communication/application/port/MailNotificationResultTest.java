package dev.persefonia.communication.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MailNotificationResultTest {
    @Test
    void supportsSentAndFailedResults() {
        assertThat(MailNotificationResult.sent().status()).isEqualTo(MailNotificationResult.Status.SENT);
        assertThat(MailNotificationResult.failed("smtp unavailable").status()).isEqualTo(MailNotificationResult.Status.FAILED);
        assertThat(MailNotificationResult.failed(" smtp unavailable ").failureReason()).isEqualTo("smtp unavailable");
    }

    @Test
    void validatesFailureReasonByStatus() {
        assertThatThrownBy(() -> new MailNotificationResult(MailNotificationResult.Status.SENT, "unexpected"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MailNotificationResult.failed(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
