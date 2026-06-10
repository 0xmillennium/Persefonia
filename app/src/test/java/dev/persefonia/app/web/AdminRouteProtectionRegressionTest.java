package dev.persefonia.app.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class AdminRouteProtectionRegressionTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminRoutesRemainUnexposed() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("Persefonia Admin"))))
                .andExpect(content().string(not(containsString("Logout"))));
        mockMvc.perform(get("/admin/"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("Persefonia Admin"))))
                .andExpect(content().string(not(containsString("Logout"))));
    }
}
