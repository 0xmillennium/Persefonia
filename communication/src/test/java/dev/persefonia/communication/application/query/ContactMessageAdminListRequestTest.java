package dev.persefonia.communication.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.communication.domain.contact.ContactMessageStatus;
import org.junit.jupiter.api.Test;

class ContactMessageAdminListRequestTest {
    @Test
    void firstPageUsesDefaultPageSizeWithoutStatusFilter() {
        ContactMessageAdminListRequest request = ContactMessageAdminListRequest.firstPage();

        assertThat(request.statusFilterOptional()).isEmpty();
        assertThat(request.page()).isEqualTo(1);
        assertThat(request.pageSize()).isEqualTo(ContactMessageAdminListRequest.DEFAULT_PAGE_SIZE);
        assertThat(request.offset()).isZero();
    }

    @Test
    void acceptsTypedStatusFilterAndCalculatesOffset() {
        ContactMessageAdminListRequest request = new ContactMessageAdminListRequest(
                ContactMessageStatus.SPAM,
                3,
                25);

        assertThat(request.statusFilterOptional()).contains(ContactMessageStatus.SPAM);
        assertThat(request.offset()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> new ContactMessageAdminListRequest(null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactMessageAdminListRequest(null, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContactMessageAdminListRequest(
                null,
                1,
                ContactMessageAdminListRequest.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
