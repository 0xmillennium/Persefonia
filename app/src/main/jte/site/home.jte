@import dev.persefonia.webpublic.PublicHomeViewModel
@param PublicHomeViewModel page
@param String publicNavigationScriptPath = null
@param java.util.List<dev.persefonia.webpublic.navigation.PublicNavigationLink> navigationLinks = java.util.List.of()
<!doctype html>
<html translate="no" class="home-document" lang="${page.htmlLanguage()}">
<head>
    @template.site.components.translationPolicy()
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="${page.defaultMetaDescription()}">
    <meta name="color-scheme" content="dark">
    <title>${page.pageTitle()}</title>
    <link rel="icon" type="image/svg+xml" href="/assets/home-mark.svg">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(false)
    @template.site.components.openGraphTags("website", page.pageTitle(), page.defaultMetaDescription(), page.canonicalUrl())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @if(publicNavigationScriptPath != null)
    <link rel="modulepreload" href="${publicNavigationScriptPath}">
    @endif
    @template.site.components.feedDiscoveryLink()
</head>
<body class="home-body">
    <a class="skip-link" href="#main-content">${page.copy().skipLink()}</a>
    <header class="home-nav">
        <div class="home-brand" data-compact-identity><button type="button" aria-label="Inspect 0xmillennium identity" disabled><span class="identity-plane">@template.site.components.compactIdentity("nav-identity")</span><span class="floor-shadow"></span></button></div>
        <a class="home-sitename" href="/" data-site-name>${page.ownerAlias().toUpperCase(java.util.Locale.ROOT)}</a>
        <button class="home-menu-toggle" type="button" data-menu-toggle aria-label="Open menu" aria-controls="home-mobile-menu" aria-expanded="false" hidden><span aria-hidden="true"></span><span aria-hidden="true"></span></button>
        <nav aria-label="${page.copy().primaryNavigation()}">
            <a href="#work"><span data-nav-label>${page.copy().projects()}</span>@if(page.projectNavigationCover().isPresent())<span class="nav-media" aria-hidden="true">@template.site.components.homeCover(page.projectNavigationCover().get(), "(max-width: 960px) 42vw, (min-width: 1375px) 330px, 24vw")</span>@endif</a>
            <a href="#writing"><span data-nav-label>${page.copy().writing()}</span>@if(page.writingNavigationCover().isPresent())<span class="nav-media" aria-hidden="true">@template.site.components.homeCover(page.writingNavigationCover().get(), "(max-width: 960px) 42vw, (min-width: 1375px) 330px, 24vw")</span>@endif</a>
            <a class="home-nav__contact" href="/contact"><span class="nav-contact-window"><span class="nav-contact-track"><span>${page.copy().contact()}</span><span aria-hidden="true">${page.copy().contact()}</span></span></span></a>
        </nav>
    </header>
    <dialog id="home-mobile-menu" class="home-menu" aria-label="${page.copy().primaryNavigation()}" data-mobile-menu>
        <div class="home-menu__bands" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i></div>
        <div class="home-menu__content"><div class="home-menu__top"><a class="home-menu__name" href="/">${page.ownerAlias().toUpperCase(java.util.Locale.ROOT)}</a><button type="button" data-menu-close aria-label="Close menu" autofocus><span aria-hidden="true">×</span></button></div>
            <nav aria-label="${page.copy().primaryNavigation()}"><a href="#work"><span data-nav-label>${page.copy().projects()}</span></a><a href="#writing"><span data-nav-label>${page.copy().writing()}</span></a><a href="/contact"><span data-nav-label>${page.copy().contact()}</span></a></nav>
        </div>
    </dialog>
    <main id="main-content" class="homepage" tabindex="-1" data-home-v2>
        <p id="film-instructions" lang="en" class="sr-only" hidden>Hold the background to change film, or press F while the main content is focused. Repeat to retry if a film is unavailable.</p>
        <div class="home-environment" aria-hidden="true">
            <div class="home-composition">
            <picture class="home-cinema__image" aria-hidden="true"><source media="(max-aspect-ratio: 4/5)" srcset="/assets/cloudy-turquoise-portrait-0283c0cd2e89.webp"><img src="/assets/cloudy-turquoise-desktop-af63954b8f41.webp" alt="" width="1920" height="1072" fetchpriority="high" data-hero-image></picture>
            <canvas class="home-film" data-film-canvas aria-hidden="true"></canvas>
            </div>
        </div>
        <section class="home-scene home-identity" id="identity" data-scene="hero" aria-labelledby="identity-title">
            <div class="scene-face hero-face"><h1 id="identity-title" class="home-masthead">${page.ownerAlias()}</h1><h2 class="hero-statement" lang="en"><span class="hero-writing-row"><span class="hero-writing-text">Clarity is</span></span><span class="hero-writing-row"><em class="hero-writing-text">engineered</em></span></h2><p class="hero-scroll" lang="en">Scroll to explore</p></div>
        </section>
        <section class="home-scene" data-scene="statements" aria-label="Perspective"><div class="scene-face statement-face"><div class="statement-top statement-slot"><p class="statement-copy" lang="en" data-second-writing><span class="statement-row"><span class="statement-word"><span class="statement-word-slot"><span class="statement-word-text">Curious</span></span></span> <span class="statement-word"><span class="statement-word-slot"><span class="statement-word-text">by</span></span></span> <span class="statement-word"><span class="statement-word-slot"><span class="statement-word-text">nature</span></span></span></span><span class="statement-row"><span class="statement-word"><span class="statement-word-slot"><span class="statement-word-text">Engineering</span></span></span> <span class="statement-word"><span class="statement-word-slot"><span class="statement-word-text">by</span></span></span> <span class="statement-word"><span class="statement-word-slot"><span class="statement-word-text">choice</span></span></span></span></p></div></div></section>
        <section class="home-scene" data-scene="capabilities" aria-label="Areas of focus"><div class="scene-face capability-face" lang="en"><p class="home-overline">Areas of focus</p><ol class="capabilities"><li><span>AI Systems</span></li><li><span>Cloud Infrastructure</span></li><li><span>Backend Engineering</span></li><li><span>DevOps &amp; Automation</span></li></ol></div></section>
        <section class="home-scene" data-scene="breath" aria-label="Space to think"><div class="scene-face breath-face" aria-hidden="true"></div></section>
        <section class="home-scene" data-scene="mark" aria-label="0xmillennium identity"><div class="scene-face mark-face"><p class="brand-statement" lang="en"><span><span>Made to think</span></span><span><span>Built to work</span></span></p><div class="physical-identity" data-physical-identity data-input-owner>@template.site.components.physicalIdentity()</div></div></section>
        <section class="home-scene home-gallery" data-scene="gallery" aria-labelledby="gallery-title"><div class="scene-face gallery-face">
            <h2 id="gallery-title" lang="en"><span class="gallery-writing-slot"><span class="gallery-writing-rise">Building systems</span></span> <span class="gallery-writing-slot"><em class="gallery-writing-rise">worth explaining</em></span></h2>
            <fieldset class="gallery-instrument" data-input-owner><legend>${page.copy().collections()}</legend>
                <div class="tuner-reveal-slot"><button class="collection-tuner" type="button" aria-haspopup="dialog" aria-expanded="false" aria-controls="collection-options" aria-label="${page.copy().collections()}">
                    <span class="tuner-scale" aria-hidden="true"></span><span class="tuner-marker" aria-hidden="true"></span>
                    <span class="tuner-names" aria-hidden="true"></span><span class="tuner-name sr-only" aria-hidden="true">${page.copy().projects()}</span>
                </button></div>
                <div class="collection-options" id="collection-options" role="dialog" aria-label="${page.copy().collections()}" hidden>
                <label><input type="radio" name="gallery-category" value="projects" checked><span>${page.copy().projects()}</span></label>
                <label><input type="radio" name="gallery-category" value="articles"><span>${page.copy().articles()}</span></label>
                <label><input type="radio" name="gallery-category" value="notes"><span>${page.copy().notes()}</span></label>
                <label><input type="radio" name="gallery-category" value="research"><span>${page.copy().research()}</span></label>
                </div>
            </fieldset>
            <div class="gallery-space">
                <section id="work" class="home-work gallery-category" data-category="projects" tabindex="-1" aria-label="${page.copy().projects()}">
                    <h2 class="sr-only">${page.copy().workTitle()}</h2>
            @if(page.promoteFeaturedProjects() && !page.featuredProjects().isEmpty())
                <ol class="home-projects gallery-group" aria-label="${page.featuredProjectsTitle()}">
                    @for(int i = 0; i < page.featuredProjects().size(); i++)
                        <li class="home-projects__item gallery-card">
                            <article class="home-project"><div class="gallery-cover"><span class="gallery-cover__fallback" aria-hidden="true">0x<span>${String.format(java.util.Locale.ROOT, "%02d", i + 1)}</span></span>@if(page.galleryCovers().containsKey(page.featuredProjects().get(i).publicUrl()))
                            @template.site.components.homeCover(page.galleryCovers().get(page.featuredProjects().get(i).publicUrl()), "100vw")
                            @endif<h3 class="gallery-card__title"><a href="${page.featuredProjects().get(i).publicUrl()}" title="${page.featuredProjects().get(i).title()}"><span><span class="gallery-card__name">${page.featuredProjects().get(i).title()}</span></span></a></h3></div>
                                <span class="home-project__number" aria-hidden="true">${String.format(java.util.Locale.ROOT, "%02d", i + 1)}</span>
                                <div class="home-project__body">
                                    <p>${page.featuredProjects().get(i).summary()}</p>
                                    @if(!page.featuredProjects().get(i).technologies().isEmpty())
                                        <ul class="home-project__tags" aria-label="${page.copy().projectTechnologies()}">
                                            @for(var technology : page.featuredProjects().get(i).technologies())
                                                <li>${technology.name()}</li>
                                            @endfor
                                        </ul>
                                    @endif
                                    @if(!page.featuredProjects().get(i).tags().isEmpty())
                                        <ul class="home-project__tags" aria-label="Tags">
                                            @for(var tag : page.featuredProjects().get(i).tags())
                                                <li>${tag.name()}</li>
                                            @endfor
                                        </ul>
                                    @endif
                                </div>
                            </article>
                        </li>
                    @endfor
                </ol>
            @endif
            @if(page.promoteFeaturedProjects() && page.featuredProjects().isEmpty())
                <p class="home-empty">${page.copy().projectsEmpty()} <a href="/contact">${page.copy().getInTouch()} ↗</a></p>
            @endif
                </section>
                <div id="writing" tabindex="-1">
                @for(var collection : page.writingCollections())
                <section id="${collection.id()}" class="home-writing gallery-category" data-category="${collection.id()}" tabindex="-1" aria-label="${collection.title()}">
                    <h2 class="gallery-collection-name">${collection.title()}</h2>
                    @if(collection.enabled() && !collection.entries().isEmpty())
                    <ol class="home-writing__list gallery-group">
                        @for(int i = 0; i < collection.entries().size(); i++)
                        <li class="gallery-card"><article><div class="gallery-cover"><span class="gallery-cover__fallback" aria-hidden="true">${collection.id().equals("research") ? "R" : collection.id().equals("notes") ? "N" : "Aa"}<span>${String.format(java.util.Locale.ROOT, "%02d", i + 1)}</span></span>@if(page.galleryCovers().containsKey(collection.entries().get(i).publicUrl()))
                            @template.site.components.homeCover(page.galleryCovers().get(collection.entries().get(i).publicUrl()), "100vw")
                            @endif<h3 class="gallery-card__title"><a href="${collection.entries().get(i).publicUrl()}" title="${collection.entries().get(i).title()}"><span><span class="gallery-card__name">${collection.entries().get(i).title()}</span></span></a></h3></div>
                            <p>${collection.entries().get(i).summary()}</p>
                            @if(page.galleryTags().containsKey(collection.entries().get(i).publicUrl()) && !page.galleryTags().get(collection.entries().get(i).publicUrl()).isEmpty())
                            <ul class="home-project__tags" aria-label="Tags">
                                @for(var tag : page.galleryTags().get(collection.entries().get(i).publicUrl()))
                                <li>${tag}</li>
                                @endfor
                            </ul>
                            @endif
                        </article></li>
                        @endfor
                    </ol>
                    @endif
                    @if(collection.enabled() && collection.entries().isEmpty())
                    <p class="home-empty">${collection.emptyMessage()} <a href="/contact">${page.copy().getInTouch()} ↗</a></p>
                    @endif
                </section>
                @endfor
                </div>
            </div>
            <output class="sr-only" data-gallery-position aria-live="polite"></output>
        </div></section>
        <section class="home-scene home-contact" id="contact" data-scene="location" aria-labelledby="contact-title" tabindex="-1"><div class="scene-face location-face">
            <div class="location-links"><h2 id="contact-title" class="sr-only">${page.copy().contactTitle()}</h2><nav aria-label="Explore"><a href="/${page.htmlLanguage()}/projects">${page.copy().projects()}</a><a href="/search">${page.copy().writing()}</a><a href="/contact">${page.copy().contact()}</a></nav><p>Istanbul, Türkiye</p><a class="contact-entry" href="/contact">${page.copy().getInTouch()} ↗</a></div>
            <div class="location-system" lang="en">    <div class="location-globe" data-input-owner>
      <canvas role="img" id="globe" aria-label="Interactive Earth. Use arrow keys to rotate and tilt." aria-describedby="globe-instructions" tabindex="-1"></canvas>
      <span id="marker" aria-hidden="true" hidden>
        <span class="marker-core"></span>
        <span class="marker-signal">
          <span class="marker-flare"></span>
          <span class="marker-echo"></span><span class="marker-echo"></span>
          <span class="marker-trace"></span><span class="marker-trace"></span>
          <span class="marker-trace"></span><span class="marker-trace"></span>
          <span class="marker-trace"></span><span class="marker-trace"></span>
          <span class="marker-trace"></span><span class="marker-trace"></span>
          <span class="marker-lock"><i></i><i></i><i></i><i></i></span>
          <span class="marker-pulse"></span><span class="marker-echo-ring"></span>
        </span>
      </span>
      <div id="location-panel" aria-hidden="true" data-open="false">
        <p>Istanbul, Türkiye</p><span>41.0082° N · 28.9784° E</span>
      </div>
      <p id="fallback">Istanbul, Türkiye · 41.0082° N, 28.9784° E<br>The interactive globe requires JavaScript and WebGL 2.</p>
    </div>
    <p id="globe-instructions" class="sr-only">Drag to turn and tilt. On touch screens, drag the globe to turn and tilt; swipe outside the globe to scroll. When the globe is focused, use arrow keys to turn and tilt, Space to pause or resume rotation, and Home to find Istanbul.</p>
    <div class="controls globe-controls" role="group" aria-label="Globe controls">
      <button type="button" id="rotate-left" aria-label="Rotate left" disabled>←</button>
      <button type="button" id="rotate-right" aria-label="Rotate right" disabled>→</button>
      <button type="button" id="tilt-up" aria-label="Tilt up" disabled>↑</button>
      <button type="button" id="tilt-down" aria-label="Tilt down" disabled>↓</button>
      <button type="button" id="pause" aria-pressed="false" disabled>Pause rotation</button>
      <button type="button" id="reset-view" disabled>Reset view</button>
    </div>
</div>
            <div class="location-strip"><span>${page.ownerAlias()} / Persefonia</span><a href="/search">${page.copy().searchPublished()}</a>@for(var link : navigationLinks)<a lang="en" href="${link.url()}">${link.label()}</a>@endfor<a href="#identity">${page.copy().backToTop()} ↑</a></div>
        </div></section>
        <div class="home-staircase" aria-hidden="true" data-scene-wipe><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
        <span data-film-status lang="en" hidden></span>
        <button class="background-motion" type="button" data-background-motion lang="en" aria-pressed="false" hidden>Pause background motion</button>
        <output class="sr-only" data-film-notice lang="en" role="status" aria-live="polite"></output>
        <output data-hold-countdown class="hold-countdown" lang="en" aria-live="polite" hidden></output>
    </main>
    <div class="home-loader" data-home-loader hidden role="status" aria-label="Preparing homepage" aria-atomic="true" lang="en">
        <div class="loader-sheet" aria-hidden="true"></div>
        <div class="loader-atmosphere" data-loader-fade aria-hidden="true"></div>
        <div class="loader-progress-slot"><div class="loading-progress" data-loading-progress data-loader-rise role="progressbar" aria-label="Homepage preparation" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"><span class="loading-progress-fill" aria-hidden="true"></span></div></div>
        <div class="loader-content">
            <div class="loader-identity-slot" aria-hidden="true"><div data-loading-identity data-loader-rise><button tabindex="-1" type="button" aria-hidden="true"><span class="identity-plane">@template.site.components.compactIdentity("loading-identity")</span><span class="floor-shadow"></span></button></div></div>
            <div class="loader-info"><p class="loader-description-slot" data-loader-rise><span class="loader-name">0XMILLENNIUM:</span> <em data-loading-description>A millennium spans 1,000 years. In hexadecimal, 1,000 becomes 0x3E8.</em></p><p class="loader-hint-slot"><span data-loading-hint data-loader-rise></span></p></div>
        </div>
        <span class="sr-only" data-loading-status>Preparing homepage</span>
        <div class="home-staircase" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
    </div>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
