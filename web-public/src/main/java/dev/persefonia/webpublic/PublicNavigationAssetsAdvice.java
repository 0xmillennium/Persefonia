package dev.persefonia.webpublic;

import java.util.Objects;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Shared destination readiness for native navigation from the immersive homepage. */
@ControllerAdvice(basePackages = "dev.persefonia.webpublic")
public final class PublicNavigationAssetsAdvice {
    private final FrontendAssetResolver assets;

    public PublicNavigationAssetsAdvice(FrontendAssetResolver assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @ModelAttribute("publicNavigationScriptPath")
    public String navigationScriptPath() {
        return assets.scriptPath("src/main.ts");
    }
}
