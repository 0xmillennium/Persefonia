package dev.persefonia.app.medialibrary.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.medialibrary.application.admin.MediaAdminCommandGateway;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalMediaAdminCommandGatewayTest {
    @Test
    void transactionalGatewayImplementsFrameworkFreeCommandGateway() {
        assertThat(MediaAdminCommandGateway.class).isAssignableFrom(TransactionalMediaAdminCommandGateway.class);
    }

    @Test
    void mutationsAreTransactional() throws Exception {
        Method upload = TransactionalMediaAdminCommandGateway.class.getMethod(
                "upload",
                dev.persefonia.medialibrary.application.admin.AdminUploadAssetCommand.class);
        Method update = TransactionalMediaAdminCommandGateway.class.getMethod(
                "updateMetadata",
                dev.persefonia.medialibrary.application.admin.UpdateAssetMetadataCommand.class);

        assertThat(upload.getAnnotation(Transactional.class)).isNotNull();
        assertThat(update.getAnnotation(Transactional.class)).isNotNull();
    }
}
