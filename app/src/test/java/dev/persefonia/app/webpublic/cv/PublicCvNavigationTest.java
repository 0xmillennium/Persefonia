package dev.persefonia.app.webpublic.cv;

import dev.persefonia.contentpublishing.application.service.PublicContentBySourceQueryHandler;
import dev.persefonia.profileportfolio.application.service.ActiveCvPublicQueryService;
import dev.persefonia.profileportfolio.domain.common.ContentLanguage;
import dev.persefonia.webpublic.content.DiscoveryPublicContentRouteResolver;
import dev.persefonia.webpublic.navigation.PublicNavigationLinks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.assertj.core.api.Assertions.assertThat;

class PublicCvNavigationTest {
    @Test
    void navigationIncludesOnlyTheRequestedLanguagesPublicAvailablePdf() {
        var profiles = new PublicCvTestConfiguration.PublicCvProfileRepositoryStub();
        var assets = new PublicCvTestConfiguration.PublicCvAssetPortStub();
        var beans = new StaticListableBeanFactory();
        beans.addBean("cv", new ActiveCvPublicQueryService(profiles,
                new PublicCvTestConfiguration.PublicCvSettingsRepositoryStub(), assets));
        var navigation = new PublicNavigationLinks(beans.getBeanProvider(ActiveCvPublicQueryService.class),
                beans.getBeanProvider(PublicContentBySourceQueryHandler.class),
                beans.getBeanProvider(DiscoveryPublicContentRouteResolver.class));
        assertThat(navigation.forLanguage("en")).isEmpty();
        profiles.profile().selectDocument(ContentLanguage.EN, PublicCvTestConfiguration.EN_PDF_ID, null, PublicCvTestConfiguration.NOW);
        assertThat(navigation.forLanguage("en")).extracting(link -> link.url()).containsExactly("/cv/en");
        assertThat(navigation.forLanguage("tr")).isEmpty();
        assets.removeContent(PublicCvTestConfiguration.EN_PDF_ID);
        assertThat(navigation.forLanguage("en")).isEmpty();
        assets.reset();
        assets.makePrivate(PublicCvTestConfiguration.EN_PDF_ID);
        assertThat(navigation.forLanguage("en")).isEmpty();
    }
}
