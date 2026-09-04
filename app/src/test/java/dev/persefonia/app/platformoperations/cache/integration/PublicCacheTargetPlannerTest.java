package dev.persefonia.app.platformoperations.cache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.persefonia.platformoperations.domain.cache.CacheTargetType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicCacheTargetPlannerTest {
    private final PublicCacheTargetPlanner planner = new PublicCacheTargetPlanner();

    @Test
    void deduplicatesSortsAndProducesUrlOnlySystemRequest() {
        var request = planner.plan(List.of("/z", "/a", "/z")).orElseThrow();

        assertThat(request.reason().name()).isEqualTo("PUBLIC_RESOURCE_CHANGED");
        assertThat(request.requestedBy().name()).isEqualTo("SYSTEM");
        assertThat(request.targets()).extracting(target -> target.value()).containsExactly("/a", "/z");
        assertThat(request.targets()).allMatch(target -> target.targetType() == CacheTargetType.URL);
    }

    @Test
    void emptyPlanDoesNotCreateARequest() {
        assertThat(planner.plan(List.of())).isEmpty();
    }

    @Test
    void exactlyFiveHundredIsAllowedAndFiveHundredOneRejectsEverything() {
        List<String> fiveHundred = routes(500);
        assertThat(planner.plan(fiveHundred).orElseThrow().targets()).hasSize(500);

        assertThatThrownBy(() -> planner.plan(routes(501)))
                .isInstanceOf(PublicCacheTargetPlanner.PublicCacheTargetOverflowException.class);
    }

    private static List<String> routes(int count) {
        List<String> routes = new ArrayList<>();
        for (int index = 0; index < count; index++) routes.add("/target/" + index);
        return routes;
    }
}
