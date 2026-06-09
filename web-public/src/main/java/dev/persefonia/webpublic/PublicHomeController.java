package dev.persefonia.webpublic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicHomeController {
    private static final String FRONTEND_ENTRY = "src/main.ts";

    private final FrontendAssetResolver assetResolver;
    private final String publicBaseUrl;
    private final String ownerAlias;

    public PublicHomeController(
            FrontendAssetResolver assetResolver,
            @Value("${site.public-base-url}") String publicBaseUrl,
            @Value("${site.owner-alias}") String ownerAlias) {
        this.assetResolver = assetResolver;
        this.publicBaseUrl = publicBaseUrl;
        this.ownerAlias = ownerAlias;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("page", new PublicHomeViewModel(
                "Persefonia",
                ownerAlias,
                publicBaseUrl,
                assetResolver.scriptPath(FRONTEND_ENTRY),
                assetResolver.stylesheetPaths(FRONTEND_ENTRY)));
        return "site/home";
    }
}
