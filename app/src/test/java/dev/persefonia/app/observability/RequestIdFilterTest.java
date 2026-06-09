package dev.persefonia.app.observability;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class RequestIdFilterTest {
    private static final String HEADER = "X-Request-Id";
    private static final String SAFE_REQUEST_ID_PATTERN = "[A-Za-z0-9._-]+";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesSafeRequestIdResponseHeader() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HEADER))
                .andExpect(header().string(HEADER, matchesPattern(SAFE_REQUEST_ID_PATTERN)));
    }

    @Test
    void ignoresIncomingRequestIdByDefault() throws Exception {
        String incomingRequestId = "valid-but-untrusted";

        String responseRequestId = mockMvc.perform(get("/").header(HEADER, incomingRequestId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HEADER);

        assertNotEquals(incomingRequestId, responseRequestId);
    }

    @Test
    void ignoresInvalidIncomingRequestIdByDefault() throws Exception {
        String incomingRequestId = "invalid request id\r\n";

        String responseRequestId = mockMvc.perform(get("/").header(HEADER, incomingRequestId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HEADER);

        assertNotEquals(incomingRequestId, responseRequestId);
    }

    @Test
    void clearsRequestIdFromMdcAfterCompletion() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());

        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}

@SpringBootTest(properties = {
        "persefonia.observability.request-id.trust-incoming-header=true",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class TrustedIncomingRequestIdFilterTest {
    private static final String HEADER = "X-Request-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsValidIncomingRequestIdWhenEnabled() throws Exception {
        String incomingRequestId = "trusted.request_id-123";

        mockMvc.perform(get("/").header(HEADER, incomingRequestId))
                .andExpect(status().isOk())
                .andExpect(header().string(HEADER, incomingRequestId));
    }

    @Test
    void rejectsInvalidIncomingRequestIdWhenEnabled() throws Exception {
        String incomingRequestId = "invalid/request/id";

        String responseRequestId = mockMvc.perform(get("/").header(HEADER, incomingRequestId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HEADER);

        assertNotEquals(incomingRequestId, responseRequestId);
    }
}
