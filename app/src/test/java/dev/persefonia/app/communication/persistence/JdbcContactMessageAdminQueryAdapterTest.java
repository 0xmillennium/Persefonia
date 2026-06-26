package dev.persefonia.app.communication.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.application.query.ContactMessageAdminListItem;
import dev.persefonia.communication.application.query.ContactMessageAdminListRequest;
import dev.persefonia.communication.domain.contact.AdminAccountId;
import dev.persefonia.communication.domain.contact.ContactMessage;
import dev.persefonia.communication.domain.contact.ContactMessageId;
import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import dev.persefonia.communication.domain.contact.ContactMessageStatusChangeId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptId;
import dev.persefonia.communication.domain.contact.MailNotificationAttemptResult;
import dev.persefonia.communication.domain.contact.SafeFailureReason;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class JdbcContactMessageAdminQueryAdapterTest extends CommunicationPersistenceTestDatabase {
    private static final Instant NOW = Instant.parse("2026-06-25T10:00:00Z");

    @Test
    void listReturnsNewestFirst() {
        ContactMessage older = JdbcContactMessageRepositoryAdapterTest.message("older", NOW);
        ContactMessage newer = JdbcContactMessageRepositoryAdapterTest.message("newer", NOW.plusSeconds(60));
        contactMessages.save(older);
        contactMessages.save(newer);

        assertThat(adminQuery.list(ContactMessageAdminListRequest.firstPage()).items())
                .extracting(item -> item.id())
                .containsExactly(newer.id(), older.id());
    }

    @Test
    void listFiltersByStatus() {
        ContactMessage fresh = JdbcContactMessageRepositoryAdapterTest.message("fresh", NOW);
        ContactMessage spam = JdbcContactMessageRepositoryAdapterTest.message("spam", NOW.plusSeconds(60));
        spam.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.SPAM,
                AdminAccountId.newId(),
                NOW.plusSeconds(70));
        contactMessages.save(fresh);
        contactMessages.save(spam);

        var page = adminQuery.list(new ContactMessageAdminListRequest(ContactMessageStatus.SPAM, 1, 20));

        assertThat(page.items()).extracting(item -> item.id()).containsExactly(spam.id());
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    void listPaginatesAndEnforcesPageSizeMaxThroughRequest() {
        contactMessages.save(JdbcContactMessageRepositoryAdapterTest.message("page-a", NOW));
        contactMessages.save(JdbcContactMessageRepositoryAdapterTest.message("page-b", NOW.plusSeconds(1)));
        contactMessages.save(JdbcContactMessageRepositoryAdapterTest.message("page-c", NOW.plusSeconds(2)));

        var page = adminQuery.list(new ContactMessageAdminListRequest(null, 2, 2));

        assertThat(page.items()).hasSize(1);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(2);
        assertThat(page.totalItems()).isEqualTo(3);
        assertThatThrownBy(() -> new ContactMessageAdminListRequest(
                null,
                1,
                ContactMessageAdminListRequest.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listItemsExcludeBodyButIncludeMailAttemptSummary() {
        ContactMessage message = JdbcContactMessageRepositoryAdapterTest.message("summary", NOW);
        message.recordMailSent(MailNotificationAttemptId.newId(), NOW.plusSeconds(1));
        message.recordMailFailed(
                MailNotificationAttemptId.newId(),
                SafeFailureReason.of("SMTP unavailable"),
                NOW.plusSeconds(2));
        contactMessages.save(message);

        var item = adminQuery.list(ContactMessageAdminListRequest.firstPage()).items().getFirst();

        assertThat(Arrays.stream(ContactMessageAdminListItem.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("body");
        assertThat(item.mailAttemptCount()).isEqualTo(2);
        assertThat(item.latestMailAttemptResultOptional()).contains(MailNotificationAttemptResult.FAILED);
    }

    @Test
    void detailIncludesBodyAttemptsAndStatusChangesNewestFirst() {
        ContactMessage message = JdbcContactMessageRepositoryAdapterTest.message("detail", NOW);
        message.recordMailSent(MailNotificationAttemptId.newId(), NOW.plusSeconds(1));
        message.recordMailFailed(
                MailNotificationAttemptId.newId(),
                SafeFailureReason.of("SMTP unavailable"),
                NOW.plusSeconds(2));
        message.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.READ,
                AdminAccountId.newId(),
                NOW.plusSeconds(3));
        message.changeStatus(
                ContactMessageStatusChangeId.newId(),
                ContactMessageStatus.REPLIED,
                AdminAccountId.newId(),
                NOW.plusSeconds(4));
        contactMessages.save(message);

        var detail = adminQuery.findDetail(message.id()).orElseThrow();

        assertThat(detail.body()).isEqualTo("Body detail");
        assertThat(detail.mailAttempts())
                .extracting(attempt -> attempt.result())
                .containsExactly(MailNotificationAttemptResult.FAILED, MailNotificationAttemptResult.SENT);
        assertThat(detail.statusChanges())
                .extracting(change -> change.newStatus())
                .containsExactly(ContactMessageStatus.REPLIED, ContactMessageStatus.READ);
    }

    @Test
    void missingDetailReturnsEmpty() {
        assertThat(adminQuery.findDetail(ContactMessageId.newId())).isEmpty();
    }
}
