package dev.persefonia.app.webpublic.content;

import dev.persefonia.app.webpublic.feed.PublicFeedTestConfiguration;
import dev.persefonia.app.webpublic.search.PublicSearchTestConfiguration;
import dev.persefonia.app.webpublic.series.PublicSeriesTestConfiguration;
import dev.persefonia.app.webpublic.sitemap.PublicSitemapTestConfiguration;
import dev.persefonia.app.webpublic.tags.PublicTagTestConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@Import({
        PublicContentTestConfiguration.class,
        PublicFeedTestConfiguration.class,
        PublicSearchTestConfiguration.class,
        PublicSeriesTestConfiguration.class,
        PublicSitemapTestConfiguration.class,
        PublicTagTestConfiguration.class
})
public class PublicNavigationMvcTestConfiguration {}
