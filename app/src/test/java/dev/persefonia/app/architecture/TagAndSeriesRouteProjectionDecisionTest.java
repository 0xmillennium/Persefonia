package dev.persefonia.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TagAndSeriesRouteProjectionDecisionTest {
    private static final Path DECISION =
            Path.of("../docs/decisions/0011-reserve-tag-and-series-public-route-projections.md");
    private static final String LEGACY_DECISION_NAME =
            "0013-" + "spri" + "nt-6-discovery-route-type-expansion.md";
    private static final Path OLD_DECISION = Path.of("../docs/decisions/" + LEGACY_DECISION_NAME);

    @Test
    void decisionArtifactExistsIsIndexedAndOldArtifactIsAbsent() throws Exception {
        assertThat(DECISION).exists().isRegularFile();
        assertThat(OLD_DECISION).doesNotExist();

        String index = Files.readString(Path.of("../docs/decisions/INDEX.md"));

        assertThat(index)
                .contains("0011-reserve-tag-and-series-public-route-projections.md")
                .contains("Reserve Tag and Series Public Route Projections")
                .doesNotContain(LEGACY_DECISION_NAME);
    }

    @Test
    void decisionLocksAcceptedTagAndSeriesRouteProjectionContract() throws Exception {
        String decision = Files.readString(DECISION);

        assertThat(decision)
                .contains("Status | Accepted")
                .contains("Reserve Tag and Series Public Route Projections")
                .contains("/{language}/tags/{tagSlug}")
                .contains("/{language}/series/{seriesSlug}")
                .contains("source_context = TAXONOMY")
                .contains("source_context = CONTENT_PUBLISHING")
                .contains("source_type = TAG")
                .contains("source_type = SERIES")
                .contains("resource_type = TAG")
                .contains("resource_type = SERIES")
                .contains("route_purpose = TAG_PAGE")
                .contains("route_purpose = SERIES_PAGE")
                .contains("language = TR or EN")
                .contains("Series pages must use `RoutePurpose.SERIES_PAGE`")
                .contains("NO_INDEX")
                .contains("NOT_ELIGIBLE");
    }

    @Test
    void decisionStaysFocusedOnDurableRouteReservationWithoutWorkflowLanguage() throws Exception {
        String decision = Files.readString(DECISION);

        assertThat(decision)
                .contains("stable public route shapes")
                .contains("requires a later explicit decision or implementation change")
                .doesNotContain("No active flag is added")
                .doesNotContain("No Discovery history table is introduced")
                .doesNotContain("Source contexts must not construct DiscoverableResource directly")
                .doesNotContain("Source contexts must not call Discovery repositories")
                .doesNotContain("UNLISTED content remains direct URL only")
                .doesNotContain("Spr" + "int")
                .doesNotContain("S6" + "-P00")
                .doesNotContain("Ste" + "p 0");
    }
}
