package dev.persefonia.app.platformoperations.cache.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationBatchId;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeResult;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import dev.persefonia.platformoperations.domain.cache.CacheTargetValue;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CloudflareCachePurgeAdapterTest {
    private static final String ENDPOINT = CloudflareCachePurgeAdapter.API_ORIGIN
            + "/client/v4/zones/0123456789abcdef0123456789abcdef/purge_cache";

    @Test
    void sendsUrlTargetsAsAbsoluteFilesWithBearerAuthentication() {
        Fixture fixture = fixture();
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"files\":[\"https://example.com/en/projects/persefonia\"]}"))
                .andExpect(request -> assertThat(request.getBody().toString()).doesNotContain("purge_everything"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        var result = fixture.adapter().purge(request(List.of(target(
                CacheTargetType.URL, "/en/projects/persefonia"))));

        assertThat(result.result()).isEqualTo(CachePurgeResult.SUCCESS);
        assertThat(result.outcomes()).allMatch(outcome -> outcome.status() == CacheTargetStatus.PURGED);
        fixture.server().verify();
    }

    @Test
    void separatesUrlAndCacheTagRequestsInDeterministicOrder() {
        Fixture fixture = fixture();
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(content().json("{\"files\":[\"https://example.com/z-last\"]}"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(content().json("{\"tags\":[\"site:public-documents\"]}"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        var result = fixture.adapter().purge(request(List.of(
                target(CacheTargetType.CACHE_TAG, "site:public-documents"),
                target(CacheTargetType.URL, "/z-last"))));

        assertThat(result.outcomes()).allMatch(outcome -> outcome.status() == CacheTargetStatus.PURGED);
        fixture.server().verify();
    }

    @Test
    void chunksAtOneHundredTargets() {
        Fixture fixture = fixture();
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(jsonPath("$.files.length()").value(100))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(jsonPath("$.files.length()").value(1))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        List<CachePurgeProviderTarget> targets = IntStream.range(0, 101)
                .mapToObj(index -> target(CacheTargetType.URL, "/items/" + index)).toList();

        var result = fixture.adapter().purge(request(targets));

        assertThat(result.outcomes()).hasSize(101)
                .allMatch(outcome -> outcome.status() == CacheTargetStatus.PURGED);
        fixture.server().verify();
    }

    @Test
    void stopsAfterFailedChunkAndFailsEveryUnexecutedTarget() {
        Fixture fixture = fixture();
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(jsonPath("$.files.length()").value(100))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));
        fixture.server().expect(once(), requestTo(ENDPOINT))
                .andExpect(jsonPath("$.files.length()").value(100))
                .andRespond(withRawStatus(429));
        List<CachePurgeProviderTarget> targets = IntStream.range(0, 201)
                .mapToObj(index -> target(CacheTargetType.URL, "/items/" + String.format("%03d", index))).toList();

        var result = fixture.adapter().purge(request(targets));

        assertThat(result.result()).isEqualTo(CachePurgeResult.FAILED);
        assertThat(result.failureReason()).isEqualTo(CachePurgeFailureReason.RATE_LIMITED);
        assertThat(result.outcomes()).filteredOn(outcome -> outcome.status() == CacheTargetStatus.PURGED).hasSize(100);
        assertThat(result.outcomes()).filteredOn(outcome -> outcome.status() == CacheTargetStatus.FAILED).hasSize(101);
        fixture.server().verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400,INVALID_TARGET", "401,AUTHENTICATION_ERROR", "403,AUTHENTICATION_ERROR",
            "404,INVALID_CONFIGURATION", "422,INVALID_TARGET", "429,RATE_LIMITED",
            "500,PROVIDER_5XX", "503,PROVIDER_5XX", "409,UNKNOWN_PROVIDER_FAILURE"
    })
    void mapsHttpFailuresToSafeReasons(int status, CachePurgeFailureReason expected) {
        Fixture fixture = fixture();
        fixture.server().expect(once(), requestTo(ENDPOINT)).andRespond(withRawStatus(status));

        var result = fixture.adapter().purge(request(List.of(target(CacheTargetType.URL, "/failure"))));

        assertThat(result.failureReason()).isEqualTo(expected);
        fixture.server().verify();
    }

    @Test
    void rejectsApplicationFailureAndMalformedSuccessResponses() {
        Fixture applicationFailure = fixture();
        applicationFailure.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"success\":false,\"errors\":[{\"message\":\"secret\"}]}",
                        MediaType.APPLICATION_JSON));
        assertThat(applicationFailure.adapter().purge(request(List.of(target(CacheTargetType.URL, "/one"))))
                .failureReason()).isEqualTo(CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE);
        applicationFailure.server().verify();

        Fixture malformed = fixture();
        malformed.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThat(malformed.adapter().purge(request(List.of(target(CacheTargetType.URL, "/two"))))
                .failureReason()).isEqualTo(CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE);
        malformed.server().verify();
    }

    @Test
    void distinguishesTimeoutFromOrdinaryNetworkFailure() {
        RestClient timeoutClient = RestClient.builder().requestFactory((uri, method) -> {
            throw new SocketTimeoutException("timeout");
        }).build();
        RestClient networkClient = RestClient.builder().requestFactory((uri, method) -> {
            throw new IOException("network");
        }).build();

        assertThat(adapter(timeoutClient).purge(request(List.of(target(CacheTargetType.URL, "/timeout"))))
                .failureReason()).isEqualTo(CachePurgeFailureReason.TIMEOUT);
        assertThat(adapter(networkClient).purge(request(List.of(target(CacheTargetType.URL, "/network"))))
                .failureReason()).isEqualTo(CachePurgeFailureReason.NETWORK_ERROR);
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(CloudflareCachePurgeAdapter.API_ORIGIN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(adapter(builder.build()), server);
    }

    private static CloudflareCachePurgeAdapter adapter(RestClient client) {
        return new CloudflareCachePurgeAdapter(client, URI.create("https://example.com"),
                "0123456789abcdef0123456789abcdef", "test-api-token");
    }

    private static CachePurgeProviderRequest request(List<CachePurgeProviderTarget> targets) {
        return new CachePurgeProviderRequest(CacheInvalidationBatchId.newId(), 1, new ArrayList<>(targets));
    }

    private static CachePurgeProviderTarget target(CacheTargetType type, String value) {
        return new CachePurgeProviderTarget(CacheInvalidationTargetId.newId(), type, CacheTargetValue.of(type, value));
    }

    private record Fixture(CloudflareCachePurgeAdapter adapter, MockRestServiceServer server) { }
}
