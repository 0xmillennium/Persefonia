@import dev.persefonia.webpublic.PublicHomeViewModel
@param PublicHomeViewModel page
<!doctype html>
<html class="home-document" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="${page.defaultMetaDescription()}">
    <meta name="color-scheme" content="light">
    <title>${page.pageTitle()}</title>
    <link rel="icon" type="image/svg+xml" href="/assets/home-mark.svg">
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(false)
    @template.site.components.openGraphTags("website", page.pageTitle(), page.defaultMetaDescription(), page.canonicalUrl())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body class="home-body">
    <a class="skip-link" href="#main-content">${page.copy().skipLink()}</a>
    <header class="home-nav home-width">
        <a class="home-nav__brand" href="/">${page.siteName()} <span aria-hidden="true">/</span> ${page.ownerAlias()}</a>
        <nav class="home-nav__links" aria-label="${page.copy().primaryNavigation()}">
            <a href="#work">${page.copy().projects()}</a><a href="#writing">${page.copy().writing()}</a><a href="#research">${page.copy().research()}</a><a href="#architecture">${page.copy().architecture()}</a><a href="#about">${page.copy().about()}</a><a href="#contact">${page.copy().contact()}</a>
        </nav>
        <details class="home-nav__mobile">
            <summary>${page.copy().menu()} <span aria-hidden="true">+</span></summary>
            <nav aria-label="${page.copy().mobileNavigation()}">
                <a href="#work">${page.copy().projects()}</a><a href="#writing">${page.copy().writing()}</a><a href="#research">${page.copy().research()}</a><a href="#architecture">${page.copy().architecture()}</a><a href="#about">${page.copy().about()}</a><a href="#contact">${page.copy().contact()}</a>
            </nav>
        </details>
    </header>
    <main id="main-content" class="homepage" tabindex="-1">
        <section class="home-identity" id="identity" aria-labelledby="identity-title" data-home-section>
            <div class="home-scene__stage home-cinema" data-home-film>
                <div class="home-cinema__image" aria-hidden="true">
                    <video class="home-cinema__video" data-home-video muted playsinline preload="none" tabindex="-1"></video>
                    <div class="home-scene__register"><span></span><span></span><span></span></div>
                </div>
                <div class="home-scene__top home-width">
                    <div class="home-scene__byline"><h1 id="identity-title" class="home-masthead">${page.ownerAlias()}</h1><p class="home-kicker">${page.copy().heroKicker()}</p></div>
                    <p class="home-identity__statement">${page.copy().heroTitleStart()} <span>${page.copy().heroTitleEmphasis()}</span></p>
                </div>
                <div class="home-scene__notation" aria-hidden="true"><span>0x</span><span>persefonia</span></div>
                <div class="home-scene__bottom home-width">
                    <p class="home-identity__intro">${page.copy().heroIntro()}</p>
                    <div class="home-actions">
                        <a class="home-button" href="#work">${page.copy().exploreWork()} <span aria-hidden="true">↘</span></a>
                        <a class="home-link" href="#writing">${page.copy().readWriting()} <span aria-hidden="true">↗</span></a>
                    </div>
                    <div class="home-cinema__controls">
                        <span class="home-cinema__caption">${page.copy().personalSite()}</span>
                        <button class="home-motion" type="button" data-motion-toggle data-pause-label="${page.copy().pauseMotion()}" data-resume-label="${page.copy().resumeMotion()}" hidden>${page.copy().pauseMotion()}</button>
                    </div>
                </div>
            </div>
        </section>
        <section class="home-section home-work" id="work" aria-labelledby="work-title" data-home-section tabindex="-1">
            <div class="home-section__heading">
                <p class="home-section__label">${page.copy().selectedWork()}</p>
                <h2 id="work-title">${page.copy().workTitle()}</h2>
                <p>${page.copy().workIntro()}</p>
            </div>
            @if(page.promoteFeaturedProjects() && !page.featuredProjects().isEmpty())
                <ol class="home-projects" aria-label="${page.featuredProjectsTitle()}">
                    @for(int i = 0; i < page.featuredProjects().size(); i++)
                        <li class="home-projects__item">
                            <article class="home-project">
                                <span class="home-project__number" aria-hidden="true">${String.format(java.util.Locale.ROOT, "%02d", i + 1)}</span>
                                <div class="home-project__body">
                                    <h3><a href="${page.featuredProjects().get(i).publicUrl()}">${page.featuredProjects().get(i).title()} <span class="home-project__arrow" aria-hidden="true">↗</span></a></h3>
                                    <p>${page.featuredProjects().get(i).summary()}</p>
                                    @if(!page.featuredProjects().get(i).technologies().isEmpty())
                                        <ul class="home-project__tags" aria-label="${page.copy().projectTechnologies()}">
                                            @for(var technology : page.featuredProjects().get(i).technologies())
                                                <li>${technology.name()}</li>
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
                <p class="home-empty">${page.copy().projectsEmpty()}</p>
            @endif
            <a class="home-section__more home-link" href="/${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}/projects">${page.copy().viewProjects()} <span aria-hidden="true">↗</span></a>
        </section>
        <div class="home-reading home-width">
        <section class="home-section home-writing" id="writing" aria-labelledby="writing-title" data-home-section tabindex="-1">
            <div class="home-section__heading">
                <h2 id="writing-title">${page.copy().writingTitle()}</h2>
                <p>${page.copy().writingIntro()}</p>
            </div>
            @if(page.promoteLatestWriting() && !page.latestWriting().isEmpty())
                <ol class="home-writing__list">
                    @for(int i = 0; i < page.latestWriting().size(); i++)
                        <li><article>
                            <p class="home-writing__number">${page.copy().writing()}</p>
                            <h3><a href="${page.latestWriting().get(i).publicUrl()}">${page.latestWriting().get(i).title()} <span aria-hidden="true">↗</span></a></h3>
                            <p>${page.latestWriting().get(i).summary()}</p>
                        </article></li>
                    @endfor
                </ol>
            @endif
            @if(page.promoteLatestWriting() && page.latestWriting().isEmpty())
                <p class="home-empty">${page.copy().writingEmpty()}</p>
            @endif
            <a class="home-section__more home-link" href="/search">${page.copy().searchPublished()} <span aria-hidden="true">↗</span></a>
        </section>
        <section class="home-section home-research" id="research" aria-labelledby="research-title" data-home-section tabindex="-1">
            <div class="home-research__inner">
                <div class="home-section__heading">
                    <p class="home-section__label">${page.copy().research()}</p>
                    <h2 id="research-title">${page.copy().researchTitle()}</h2>
                    <p>${page.copy().researchIntro()}</p>
                </div>
                @if(page.promoteResearchHighlights() && !page.researchHighlights().isEmpty())
                    <ol class="home-research__list">
                        @for(var research : page.researchHighlights())
                            <li><article><h3><a href="${research.publicUrl()}">${research.title()} <span aria-hidden="true">↗</span></a></h3><p>${research.summary()}</p></article></li>
                        @endfor
                    </ol>
                @endif
                @if(page.promoteResearchHighlights() && page.researchHighlights().isEmpty())
                    <p class="home-empty">${page.copy().researchEmpty()}</p>
                @endif
                <a class="home-section__more home-link" href="/search">${page.copy().searchPublished()} <span aria-hidden="true">↗</span></a>
            </div>
        </section>
        </div>
        <section class="home-section home-architecture home-width" id="architecture" aria-labelledby="architecture-title" data-home-section tabindex="-1">
            <div class="home-section__heading">
                <p class="home-section__label">${page.copy().systemColophon()}</p>
                <h2 id="architecture-title">${page.copy().architectureTitle()}</h2>
                <p>${page.copy().architectureIntro()}</p>
                <p class="home-architecture__hint">${page.copy().inspectSystem()}</p>
            </div>
            <div class="home-architecture__structure" aria-label="${page.copy().architectureLabel()}">
                <details class="home-layer">
                    <summary><span class="home-layer__index" aria-hidden="true">01</span><span><strong>${page.copy().publicExperience()}</strong><span>${page.copy().publicExperienceDetail()}</span></span><span class="home-layer__expand" aria-hidden="true">+</span></summary>
                    <p>${page.copy().publicExperienceEvidence()}</p>
                </details>
                <details class="home-layer">
                    <summary><span class="home-layer__index" aria-hidden="true">02</span><span><strong>${page.copy().application()}</strong><span>${page.copy().applicationDetail()}</span></span><span class="home-layer__expand" aria-hidden="true">+</span></summary>
                    <p>${page.copy().applicationEvidence()}</p>
                </details>
                <details class="home-layer">
                    <summary><span class="home-layer__index" aria-hidden="true">03</span><span><strong>${page.copy().domain()}</strong><span>${page.copy().domainDetail()}</span></span><span class="home-layer__expand" aria-hidden="true">+</span></summary>
                    <p>${page.copy().domainEvidence()}</p>
                </details>
                <details class="home-layer">
                    <summary><span class="home-layer__index" aria-hidden="true">04</span><span><strong>${page.copy().durableState()}</strong><span>${page.copy().durableStateDetail()}</span></span><span class="home-layer__expand" aria-hidden="true">+</span></summary>
                    <p>${page.copy().durableStateEvidence()}</p>
                </details>
            </div>
        </section>
        <section class="home-section home-about home-width" id="about" aria-labelledby="about-title" data-home-section tabindex="-1">
            <div class="home-section__heading"><h2 id="about-title">${page.copy().aboutTitle()}</h2></div>
            <div class="home-about__body">
                <p class="home-about__alias">${page.ownerAlias()}</p>
                @if(page.profile().isPresent())
                    <p class="home-about__name">${page.profile().get().displayName()}</p>
                    <p class="home-about__bio">${page.profile().get().shortBio()}</p>
                    @if(!page.profile().get().locationText().isBlank())
                        <p class="home-about__location">${page.profile().get().locationText()}</p>
                    @endif
                    @if(!page.profile().get().currentFocusItems().isEmpty())
                        <div class="home-about__focus"><h3>${page.copy().currentFocus()}</h3><ul>
                            @for(var item : page.profile().get().currentFocusItems())
                                <li>${item.text()}</li>
                            @endfor
                        </ul></div>
                    @endif
                    @if(!page.profile().get().externalLinks().isEmpty())
                        <ul class="home-about__links">
                            @for(var link : page.profile().get().externalLinks())
                                <li><a href="${link.url()}" target="_blank" rel="noopener noreferrer">${link.label()} <span aria-hidden="true">↗</span></a></li>
                            @endfor
                        </ul>
                    @endif
                @endif
            </div>
        </section>
        <section class="home-section home-contact" id="contact" aria-labelledby="contact-title" data-home-section tabindex="-1">
            <div class="home-width">
                <div class="home-section__heading"><h2 id="contact-title">${page.copy().contactTitle()}</h2><p>${page.copy().contactIntro()}</p></div>
                <a class="home-contact__link" href="/contact">${page.copy().getInTouch()} <span aria-hidden="true">↗</span></a>
            </div>
        </section>
    </main>
    <footer class="home-footer home-width"><span>${page.siteName()} / ${page.ownerAlias()}</span><a class="home-link" href="#identity">${page.copy().backToTop()} <span aria-hidden="true">↑</span></a></footer>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
