package dev.persefonia.app.security.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SensitiveRouteCacheHeadersFilterTest {
    private final SensitiveRouteCacheHeadersFilter filter = new SensitiveRouteCacheHeadersFilter();

    @Test
    void administratorDoesNotMatchAdminRoute() throws Exception {
        MockHttpServletResponse response = filter("/administrator");

        assertThat(response.getHeader("Cache-Control")).isNull();
        assertThat(response.getHeader("Pragma")).isNull();
        assertThat(response.getHeader("Expires")).isNull();
    }

    @Test
    void nestedAdminRouteMatchesAdminPrefix() throws Exception {
        assertThat(filter("/admin/example").getHeader("Cache-Control"))
                .isEqualTo(SensitiveRouteCacheHeadersFilter.CACHE_CONTROL);
    }

    private MockHttpServletResponse filter(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
