package dev.persefonia.app.assets;

import dev.persefonia.webpublic.FrontendAssetResolver;
import java.util.List;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "dev.persefonia.webadmin")
public final class AdminStylesAdvice {
    private final FrontendAssetResolver assets;

    public AdminStylesAdvice(FrontendAssetResolver assets) {
        this.assets = assets;
    }

    @ModelAttribute("adminStylesheetPaths")
    public List<String> stylesheetPaths() {
        return assets.stylesheetPaths("src/main.ts");
    }
}
