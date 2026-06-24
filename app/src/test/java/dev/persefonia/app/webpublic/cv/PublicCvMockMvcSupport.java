package dev.persefonia.app.webpublic.cv;

import dev.persefonia.app.webpublic.cv.PublicCvTestConfiguration.PublicCvAssetPortStub;
import dev.persefonia.app.webpublic.cv.PublicCvTestConfiguration.PublicCvProfileRepositoryStub;
import dev.persefonia.app.webpublic.cv.PublicCvTestConfiguration.PublicCvSettingsRepositoryStub;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicDownloadService;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.webpublic.FrontendAssetResolver;
import dev.persefonia.webpublic.content.PublicCanonicalUrlFactory;
import dev.persefonia.webpublic.content.PublicContentResponseHeaders;
import dev.persefonia.webpublic.content.PublicContentViewModelFactory;
import dev.persefonia.webpublic.cv.PublicCvController;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

final class PublicCvMockMvcSupport {
    final MockMvc mockMvc;
    final PublicCvProfileRepositoryStub profiles;
    final PublicCvAssetPortStub assets;

    private PublicCvMockMvcSupport(
            MockMvc mockMvc,
            PublicCvProfileRepositoryStub profiles,
            PublicCvAssetPortStub assets) {
        this.mockMvc = mockMvc;
        this.profiles = profiles;
        this.assets = assets;
    }

    static PublicCvMockMvcSupport create() {
        PublicCvProfileRepositoryStub profiles = new PublicCvProfileRepositoryStub();
        PublicCvSettingsRepositoryStub settings = new PublicCvSettingsRepositoryStub();
        PublicCvAssetPortStub assets = new PublicCvAssetPortStub();
        ActiveCvPublicQueryService queryService = new ActiveCvPublicQueryService(profiles, settings, assets);
        ActiveCvPublicDownloadService downloadService = new ActiveCvPublicDownloadService(profiles, settings, assets);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("activeCvPublicQueryService", queryService);
        beans.addBean("activeCvPublicDownloadService", downloadService);

        PublicCvController controller = new PublicCvController(
                beans.getBeanProvider(ActiveCvPublicQueryService.class),
                beans.getBeanProvider(ActiveCvPublicDownloadService.class),
                new PublicContentResponseHeaders(),
                new PublicContentViewModelFactory(new TestAssetResolver(), new PublicCanonicalUrlFactory("https://example.test")));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(new JteViewResolver())
                .build();
        return new PublicCvMockMvcSupport(mockMvc, profiles, assets);
    }

    void reset() {
        profiles.reset();
        assets.reset();
    }

    private static final class JteViewResolver implements ViewResolver {
        private final TemplateEngine templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);

        @Override
        public View resolveViewName(String viewName, Locale locale) {
            return (model, request, response) -> {
                response.setContentType("text/html;charset=UTF-8");
                StringOutput output = new StringOutput();
                templateEngine.render(viewName + ".jte", model.get("page"), output);
                response.getWriter().write(output.toString());
            };
        }
    }

    private static final class TestAssetResolver implements FrontendAssetResolver {
        @Override
        public String scriptPath(String entry) {
            return "/assets/main-test.js";
        }

        @Override
        public List<String> stylesheetPaths(String entry) {
            return List.of("/assets/main-test.css");
        }
    }
}
