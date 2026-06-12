package dev.persefonia.app.contentpublishing.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.persefonia.contentpublishing.application.command.ArchiveContentCommand;
import dev.persefonia.contentpublishing.application.command.CreateContentDraftCommand;
import dev.persefonia.contentpublishing.application.command.PreviewContentCommand;
import dev.persefonia.contentpublishing.application.command.PublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UnpublishContentCommand;
import dev.persefonia.contentpublishing.application.command.UpdateContentDraftCommand;
import dev.persefonia.contentpublishing.application.service.ContentCommandGateway;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalContentGatewayArchitectureTest {
    @Test
    void transactionalGatewayImplementsFrameworkFreeCommandGateway() {
        assertThat(ContentCommandGateway.class).isAssignableFrom(TransactionalContentApplicationGateway.class);
    }

    @Test
    void commandMethodsDeclareRequiredTransactionBoundaries() throws NoSuchMethodException {
        assertReadWrite("createDraft", CreateContentDraftCommand.class);
        assertReadWrite("updateDraft", UpdateContentDraftCommand.class);
        assertReadOnly("previewContent", PreviewContentCommand.class);
        assertReadWrite("publishContent", PublishContentCommand.class);
        assertReadWrite("unpublishContent", UnpublishContentCommand.class);
        assertReadWrite("archiveContent", ArchiveContentCommand.class);
    }

    private static void assertReadWrite(String methodName, Class<?> commandType) throws NoSuchMethodException {
        Transactional transactional = method(methodName, commandType).getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private static void assertReadOnly(String methodName, Class<?> commandType) throws NoSuchMethodException {
        Transactional transactional = method(methodName, commandType).getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    private static Method method(String methodName, Class<?> commandType) throws NoSuchMethodException {
        return TransactionalContentApplicationGateway.class.getDeclaredMethod(methodName, commandType);
    }
}
