package dev.persefonia.app.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void logsSafeStructuredRequestCompletion(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/?password=super-secret-value&token=super-secret-token")
                        .header("Authorization", "Bearer should-not-appear")
                        .header("Cookie", "session=should-not-appear")
                        .header("User-Agent", "should-not-appear")
                        .header("X-Request-Id", "should-not-be-trusted-by-default"))
                .andExpect(status().isOk());

        String logs = output.getOut();
        assertEquals(1, logs.split("http_request_completed", -1).length - 1);
        assertTrue(logs.contains("\"request_id\""));
        assertTrue(logs.contains("\"method\""));
        assertTrue(logs.contains("\"path\""));
        assertTrue(logs.contains("\"status\""));
        assertTrue(logs.contains("\"duration_ms\""));
        assertTrue(logs.contains("GET"));

        assertFalse(logs.contains("super-secret-value"));
        assertFalse(logs.contains("super-secret-token"));
        assertFalse(logs.contains("should-not-appear"));
        assertFalse(logs.contains("Authorization"));
        assertFalse(logs.contains("Cookie"));
        assertFalse(logs.contains("User-Agent"));
        assertFalse(logs.contains("session="));
        assertFalse(logs.contains("Bearer"));
        assertFalse(logs.contains("password="));
        assertFalse(logs.contains("token="));
    }
}
