package dev.persefonia.webpublic;

import dev.persefonia.medialibrary.application.publicview.PublicImageVariantView;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PublicHomeCoverViewTest {
    @Test
    void exposesRealWidthsDeduplicatesCandidatesAndKeepsMediumAsDefault() {
        var cover = new PublicHomeCoverView(List.of(image("large", 1600), image("medium", 960),
                image("thumbnail", 320), image("duplicate", 320)));
        assertThat(cover.image().variantName()).isEqualTo("medium");
        assertThat(cover.sourceSet()).isEqualTo("/thumbnail 320w, /medium 960w, /large 1600w");
        assertThat(new PublicHomeCoverView(List.of(image("thumbnail", 320))).image().width()).isEqualTo(320);
        assertThatIllegalArgumentException().isThrownBy(() -> new PublicHomeCoverView(List.of()));
    }
    private static PublicImageVariantView image(String name, int width) {
        return new PublicImageVariantView("asset", name, "/" + name, width, width / 2, "image/webp", 10, true, "");
    }
}
