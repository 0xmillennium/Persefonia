package dev.persefonia.app.security.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SensitiveRouteCacheHeadersFilterTest {
    private final SensitiveRouteCacheHeadersFilter filter = new SensitiveRouteCacheHeadersFilter();

    @Test
    void matchesAdmin() throws Exception {
        assertNoStore(filter("/admin"));
    }

    @Test
    void matchesAdminSubpath() throws Exception {
        assertNoStore(filter("/admin/example"));
    }

    @Test
    void doesNotMatchAdministrator() throws Exception {
        assertNoStoreAbsent(filter("/administrator"));
    }

    @Test
    void matchesLogout() throws Exception {
        assertNoStore(filter("/logout"));
    }

    @Test
    void matchesOauth2Authorization() throws Exception {
        assertNoStore(filter("/oauth2/authorization/authelia"));
    }

    @Test
    void matchesLoginOauth2Callback() throws Exception {
        assertNoStore(filter("/login/oauth2/code/authelia"));
    }

    @Test
    void matchesAdminWhenContextPathIsPresent() throws Exception {
        assertNoStore(filter("/persefonia/admin", "/persefonia"));
    }

    @Test
    void matchesOauth2WhenContextPathIsPresent() throws Exception {
        assertNoStore(filter("/persefonia/oauth2/authorization/authelia", "/persefonia"));
    }

    private static void assertNoStore(MockHttpServletResponse response) {
        assertThat(response.getHeader("Cache-Control")).isEqualTo(SensitiveRouteCacheHeadersFilter.CACHE_CONTROL);
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getDateHeader("Expires")).isZero();
    }

    private static void assertNoStoreAbsent(MockHttpServletResponse response) {
        assertThat(response.getHeader("Cache-Control")).isNull();
        assertThat(response.getHeader("Pragma")).isNull();
        assertThat(response.getHeader("Expires")).isNull();
    }

    private MockHttpServletResponse filter(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return filter(request);
    }

    private MockHttpServletResponse filter(String requestUri, String contextPath) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setContextPath(contextPath);
        request.setRequestURI(requestUri);
        return filter(request);
    }

    private MockHttpServletResponse filter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
