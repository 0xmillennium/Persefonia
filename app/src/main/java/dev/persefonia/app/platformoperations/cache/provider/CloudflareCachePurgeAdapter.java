package dev.persefonia.app.platformoperations.cache.provider;

import dev.persefonia.platformoperations.application.cache.CachePurgePort;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderRequest;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderResult;
import dev.persefonia.platformoperations.application.cache.CachePurgeProviderTarget;
import dev.persefonia.platformoperations.domain.cache.CacheInvalidationTargetId;
import dev.persefonia.platformoperations.domain.cache.CachePurgeFailureReason;
import dev.persefonia.platformoperations.domain.cache.CachePurgeProvider;
import dev.persefonia.platformoperations.domain.cache.CacheTargetOutcome;
import dev.persefonia.platformoperations.domain.cache.CacheTargetStatus;
import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class CloudflareCachePurgeAdapter implements CachePurgePort {
    public static final String API_ORIGIN = "https://api.cloudflare.com";
    static final int MAX_TARGETS_PER_REQUEST = 100;

    private static final Comparator<CachePurgeProviderTarget> TARGET_ORDER = Comparator
            .comparing((CachePurgeProviderTarget target) -> target.targetValue().value())
            .thenComparing(target -> target.targetId().value());

    private final RestClient client;
    private final URI publicBaseOrigin;
    private final String zoneId;
    private final String apiToken;

    public CloudflareCachePurgeAdapter(
            RestClient client, URI publicBaseOrigin, String zoneId, String apiToken) {
        this.client = Objects.requireNonNull(client, "client");
        this.publicBaseOrigin = Objects.requireNonNull(publicBaseOrigin, "publicBaseOrigin");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        this.apiToken = Objects.requireNonNull(apiToken, "apiToken");
    }

    @Override
    public CachePurgeProvider provider() {
        return CachePurgeProvider.CLOUDFLARE;
    }

    @Override
    public CachePurgeProviderResult purge(CachePurgeProviderRequest request) {
        List<ProviderChunk> chunks = chunks(request);
        Map<CacheInvalidationTargetId, CacheTargetStatus> statuses = new LinkedHashMap<>();
        CachePurgeFailureReason failureReason = null;

        for (ProviderChunk chunk : chunks) {
            failureReason = execute(chunk);
            if (failureReason != null) {
                break;
            }
            chunk.targets().forEach(target -> statuses.put(target.targetId(), CacheTargetStatus.PURGED));
        }

        if (failureReason != null) {
            request.targets().forEach(target -> statuses.putIfAbsent(target.targetId(), CacheTargetStatus.FAILED));
        }
        List<CacheTargetOutcome> outcomes = request.targets().stream()
                .map(target -> CacheTargetOutcome.of(target.targetId(), statuses.get(target.targetId())))
                .toList();
        return failureReason == null
                ? CachePurgeProviderResult.success(request, outcomes)
                : CachePurgeProviderResult.failed(request, failureReason, outcomes);
    }

    private List<ProviderChunk> chunks(CachePurgeProviderRequest request) {
        List<ProviderChunk> chunks = new ArrayList<>();
        appendChunks(chunks, request.targets().stream()
                .filter(target -> target.targetType() == CacheTargetType.URL)
                .sorted(TARGET_ORDER).toList(), "files");
        appendChunks(chunks, request.targets().stream()
                .filter(target -> target.targetType() == CacheTargetType.CACHE_TAG)
                .sorted(TARGET_ORDER).toList(), "tags");
        return chunks;
    }

    private static void appendChunks(
            List<ProviderChunk> destination, List<CachePurgeProviderTarget> targets, String operation) {
        for (int start = 0; start < targets.size(); start += MAX_TARGETS_PER_REQUEST) {
            destination.add(new ProviderChunk(operation,
                    targets.subList(start, Math.min(start + MAX_TARGETS_PER_REQUEST, targets.size()))));
        }
    }

    private CachePurgeFailureReason execute(ProviderChunk chunk) {
        List<String> values = chunk.targets().stream()
                .map(target -> chunk.operation().equals("files")
                        ? publicBaseOrigin.resolve(target.targetValue().value()).toString()
                        : target.targetValue().value())
                .toList();
        try {
            CloudflareResponse response = client.post()
                    .uri("/client/v4/zones/{zoneId}/purge_cache", zoneId)
                    .headers(headers -> headers.setBearerAuth(apiToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(chunk.operation(), values))
                    .retrieve()
                    .body(CloudflareResponse.class);
            return response != null && Boolean.TRUE.equals(response.success())
                    ? null
                    : CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE;
        } catch (ResourceAccessException transportFailure) {
            return CloudflareFailureClassifier.transport(transportFailure);
        } catch (RestClientResponseException responseFailure) {
            return CloudflareFailureClassifier.http(responseFailure.getStatusCode());
        } catch (RestClientException malformedOrUnexpectedResponse) {
            return CachePurgeFailureReason.UNKNOWN_PROVIDER_FAILURE;
        }
    }

    private record ProviderChunk(String operation, List<CachePurgeProviderTarget> targets) {
        private ProviderChunk {
            targets = List.copyOf(targets);
        }
    }

    private record CloudflareResponse(Boolean success) { }
}
