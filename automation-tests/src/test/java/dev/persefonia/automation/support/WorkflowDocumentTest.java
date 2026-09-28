package dev.persefonia.automation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowDocumentTest {
    @Test
    void namedStepLookupRequiresExactlyOneNonBlankNamePerJob() throws IOException {
        WorkflowDocument workflow = WorkflowDocument.open("ci.yml");
        assertThat(workflow.step("automation", "Check out repository")).containsKey("uses");
        assertThatThrownBy(() -> workflow.step("automation", "missing"))
                .isInstanceOf(IllegalArgumentException.class);

        workflow.steps("automation").add(Map.of("name", "Check out repository", "run", "echo duplicate"));
        assertThatThrownBy(() -> workflow.step("automation", "Check out repository"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate step name");

        WorkflowDocument unnamed = WorkflowDocument.open("ci.yml");
        unnamed.steps("automation").add(Map.of("name", " ", "run", "echo unnamed"));
        assertThatThrownBy(() -> unnamed.steps("automation"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("step name");
    }
}
