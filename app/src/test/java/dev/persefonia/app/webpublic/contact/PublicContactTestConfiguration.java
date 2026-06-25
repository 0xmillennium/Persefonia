package dev.persefonia.app.webpublic.contact;

import dev.persefonia.webpublic.contact.PublicContactSubmissionGateway;
import dev.persefonia.webpublic.contact.PublicContactSubmissionRequest;
import dev.persefonia.webpublic.contact.PublicContactSubmissionResult;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration(proxyBeanMethods = false)
@Profile("public-contact-mvc-test")
class PublicContactTestConfiguration {
    @Bean
    @Primary
    TrackingPublicContactSubmissionGateway trackingPublicContactSubmissionGateway() {
        return new TrackingPublicContactSubmissionGateway();
    }

    static final class TrackingPublicContactSubmissionGateway implements PublicContactSubmissionGateway {
        private int calls;
        private PublicContactSubmissionRequest lastRequest;
        private PublicContactSubmissionResult nextResult = PublicContactSubmissionResult.success();

        @Override
        public PublicContactSubmissionResult submit(PublicContactSubmissionRequest request) {
            calls++;
            lastRequest = request;
            return nextResult;
        }

        void reset() {
            calls = 0;
            lastRequest = null;
            nextResult = PublicContactSubmissionResult.success();
        }

        void invalid() {
            nextResult = PublicContactSubmissionResult.invalid(Map.of(
                    "senderEmail", "sender email must be valid",
                    "body", "contact body must not be blank"));
        }

        void rateLimited() {
            nextResult = PublicContactSubmissionResult.rateLimited();
        }

        void temporarilyUnavailable() {
            nextResult = PublicContactSubmissionResult.temporarilyUnavailable();
        }

        int calls() {
            return calls;
        }

        PublicContactSubmissionRequest lastRequest() {
            return lastRequest;
        }
    }
}
