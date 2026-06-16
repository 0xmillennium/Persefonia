package dev.persefonia.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@Import(PublicHomeTestConfiguration.class)
@ActiveProfiles({"test", "public-home-mvc-test"})
class PublicHomeRenderingTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersPublicHomeWithProductionAssets() throws Exception {
        String response = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Settings Driven Site")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Settings Driven Site | Home")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Configured homepage description.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("0xmillennium")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<link rel=\"stylesheet\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<script type=\"module\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/assets/")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(response.contains("http://localhost"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("@vite/client"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("Fake project"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("Fake profile"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("Fake content"));
    }
}
