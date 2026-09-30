@import dev.persefonia.webpublic.PublicHomeViewModel
@param PublicHomeViewModel page
<!doctype html>
<html lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="${page.defaultMetaDescription()}">
    <meta name="color-scheme" content="dark">
    <title>${page.pageTitle()}</title>
    @template.site.components.canonicalLink(page.canonicalUrl())
    @template.site.components.robotsMeta(false)
    @template.site.components.openGraphTags("website", page.pageTitle(), page.defaultMetaDescription(), page.canonicalUrl())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body class="home-body">
    <a class="skip-link" href="#main-content">${page.copy().skipLink()}</a>
    <main id="main-content" class="homepage">
        <div class="archive-stage" aria-hidden="true">
            <div class="archive-stage__folio" data-archive-folio>
                <svg class="archive-stage__geometry" viewBox="0 0 560 640" aria-hidden="true" focusable="false">
                    <line data-archive-spine class="archive-stage__geometry-spine" />
                    <line data-archive-seam class="archive-stage__geometry-seam" />
                    <line data-archive-branch class="archive-stage__geometry-branch" />
                    <circle data-archive-trace class="archive-stage__geometry-trace" r="3" />
                    <circle data-archive-joint class="archive-stage__geometry-joint" r="4" />
                </svg>
                <span class="archive-stage__sheet archive-stage__sheet--a" data-archive-sheet="0"><span class="archive-stage__index">§ 01</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.i</span></span>
                <span class="archive-stage__sheet archive-stage__sheet--b" data-archive-sheet="1"><span class="archive-stage__index">§ 02</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.ii</span></span>
                <span class="archive-stage__sheet archive-stage__sheet--c" data-archive-sheet="2"><span class="archive-stage__index">§ 03</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.iii</span></span>
                <span class="archive-stage__sheet archive-stage__sheet--d" data-archive-sheet="3"><span class="archive-stage__index">§ 04</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.iv</span></span>
                <span class="archive-stage__vellum" data-archive-vellum></span>
            </div>
        </div>
        <section class="home-section home-identity" id="identity" data-archive-chapter="0" aria-labelledby="identity-title">
            <div class="home-section__inner">
                <div class="archive-snapshot archive-snapshot--identity" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
                <header class="home-nav">
                    <a class="home-nav__brand" href="/">${page.siteName()}</a>
                    <nav class="home-nav__links" aria-label="${page.copy().primaryNavigation()}">
                        <a href="#work">${page.copy().projects()}</a><a href="#writing">${page.copy().writing()}</a><a href="#research">${page.copy().research()}</a><a href="#about">${page.copy().about()}</a>
                    </nav>
                    <details class="home-nav__mobile">
                        <summary>${page.copy().menu()}</summary>
                        <nav aria-label="${page.copy().mobileNavigation()}">
                            <a href="#work">${page.copy().projects()}</a><a href="#writing">${page.copy().writing()}</a><a href="#research">${page.copy().research()}</a><a href="#about">${page.copy().about()}</a><a href="#contact">${page.copy().contact()}</a>
                        </nav>
                    </details>
                </header>
                <div class="home-identity__copy">
                    <p class="home-kicker">${page.copy().heroKicker()}</p>
                    <h1 id="identity-title">${page.copy().heroTitleStart()} <em>${page.copy().heroTitleEmphasis()}</em></h1>
                    <p class="home-identity__intro">${page.copy().heroIntro()}</p>
                    <div class="home-actions">
                        <a class="home-button" href="#work">${page.copy().exploreWork()} <span aria-hidden="true">↗</span></a>
                        <a class="home-link" href="#writing">${page.copy().readWriting()} <span aria-hidden="true">↗</span></a>
                    </div>
                </div>
                <div class="home-section__foot"><span>01 / 07&nbsp; ${page.copy().identity()}</span><span>${page.copy().by()} ${page.ownerAlias()}</span></div>
            </div>
        </section>
        <section class="home-section home-work" id="work" data-archive-chapter="1" aria-labelledby="work-title">
            <div class="home-section__inner">
                <div class="archive-snapshot archive-snapshot--work" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
                <div class="home-section__heading">
                    <p class="home-kicker">02 / ${page.copy().selectedWork()}</p>
                    <h2 id="work-title">${page.copy().workTitle()}</h2>
                    <p>${page.copy().workIntro()}</p>
                </div>
                @if(page.promoteFeaturedProjects() && !page.featuredProjects().isEmpty())
                    <ol class="home-projects" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}" aria-label="${page.featuredProjectsTitle()}">
                        @for(int i = 0; i < page.featuredProjects().size(); i++)
                            <li class="home-projects__item">
                                <article class="home-project">
                                    <p class="home-project__meta">${i + 1} / ${page.featuredProjectsTitle()}</p>
                                    <h3><a href="${page.featuredProjects().get(i).publicUrl()}">${page.featuredProjects().get(i).title()}</a></h3>
                                    <p>${page.featuredProjects().get(i).summary()}</p>
                                    @if(!page.featuredProjects().get(i).technologies().isEmpty())
                                        <ul class="home-project__tags">
                                            @for(var technology : page.featuredProjects().get(i).technologies())
                                                <li>${technology.name()}</li>
                                            @endfor
                                        </ul>
                                    @endif
                                    <a class="home-link" href="${page.featuredProjects().get(i).publicUrl()}">${page.copy().exploreProject()} <span aria-hidden="true">↗</span></a>
                                </article>
                            </li>
                        @endfor
                    </ol>
                @endif
                @if(page.promoteFeaturedProjects() && page.featuredProjects().isEmpty())
                    <p class="home-empty">${page.copy().projectsEmpty()}</p>
                @endif
                <a class="home-section__more" href="/${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}/projects">${page.copy().viewProjects()} <span aria-hidden="true">↗</span></a>
                <div class="home-section__foot"><span>02 / 07&nbsp; ${page.copy().selectedWork()}</span></div>
            </div>
        </section>
        <section class="home-section home-writing" id="writing" data-archive-chapter="2" aria-labelledby="writing-title">
            <div class="home-section__inner">
                <div class="home-section__heading">
                    <p class="home-kicker">03 / ${page.copy().writing()}</p>
                    <h2 id="writing-title">${page.copy().writingTitle()}</h2>
                    <p>${page.copy().writingIntro()}</p>
                </div>
                @if(page.promoteLatestWriting() && !page.latestWriting().isEmpty())
                    <ol class="home-writing__list" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
                        @for(int i = 0; i < page.latestWriting().size(); i++)
                            <li><article>
                                <p class="home-writing__number">${i + 1} / ${page.copy().writing()}</p>
                                <h3><a href="${page.latestWriting().get(i).publicUrl()}">${page.latestWriting().get(i).title()}</a></h3>
                                <p>${page.latestWriting().get(i).summary()}</p>
                            </article></li>
                        @endfor
                    </ol>
                @endif
                @if(page.promoteLatestWriting() && page.latestWriting().isEmpty())
                    <p class="home-empty">${page.copy().writingEmpty()}</p>
                @endif
                <div class="home-section__foot"><span>03 / 07&nbsp; ${page.copy().writing()}</span></div>
            </div>
        </section>
        <section class="home-section home-research" id="research" data-archive-chapter="3" aria-labelledby="research-title">
            <div class="home-section__inner">
                <div class="home-section__heading">
                    <p class="home-kicker">04 / ${page.copy().research()}</p>
                    <h2 id="research-title">${page.copy().researchTitle()}</h2>
                    <p>${page.copy().researchIntro()}</p>
                </div>
                @if(page.promoteResearchHighlights() && !page.researchHighlights().isEmpty())
                    <ol class="home-research__list" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
                        @for(var research : page.researchHighlights())
                            <li><article><p class="home-project__meta">${page.copy().researchRecord()}</p><h3><a href="${research.publicUrl()}">${research.title()}</a></h3><p>${research.summary()}</p></article></li>
                        @endfor
                    </ol>
                @endif
                @if(page.promoteResearchHighlights() && page.researchHighlights().isEmpty())
                    <p class="home-empty">${page.copy().researchEmpty()}</p>
                @endif
                <div class="home-section__foot"><span>04 / 07&nbsp; ${page.copy().research()}</span></div>
            </div>
        </section>
        <section class="home-section home-architecture" id="architecture" data-archive-chapter="4" aria-labelledby="architecture-title">
            <div class="home-section__inner">
                <div class="archive-snapshot archive-snapshot--architecture" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
                <div class="home-section__heading">
                    <p class="home-kicker">05 / ${page.copy().architecture()}</p>
                    <h2 id="architecture-title">${page.copy().architectureTitle()}</h2>
                    <p>${page.copy().architectureIntro()}</p>
                </div>
                <div class="home-architecture__structure" aria-label="${page.copy().architectureLabel()}">
                    <div><span>01 / ${page.copy().publicExperience()}</span><strong>${page.copy().publicExperienceDetail()}</strong></div>
                    <div><span>02 / ${page.copy().application()}</span><strong>${page.copy().applicationDetail()}</strong></div>
                    <div><span>03 / ${page.copy().domain()}</span><strong>${page.copy().domainDetail()}</strong></div>
                    <div><span>04 / ${page.copy().durableState()}</span><strong>${page.copy().durableStateDetail()}</strong></div>
                </div>
                <div class="home-section__foot"><span>05 / 07&nbsp; ${page.copy().architecture()}</span></div>
            </div>
        </section>
        <section class="home-section home-about" id="about" data-archive-chapter="5" aria-labelledby="about-title">
            <div class="home-section__inner">
                <div class="home-section__heading">
                    <p class="home-kicker">06 / ${page.copy().about()}</p>
                    <h2 id="about-title">${page.copy().aboutTitle()}</h2>
                </div>
                <div class="home-about__body" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
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
                <div class="home-section__foot"><span>06 / 07&nbsp; ${page.copy().about()}</span></div>
            </div>
        </section>
        <section class="home-section home-contact" id="contact" data-archive-chapter="6" aria-labelledby="contact-title">
            <div class="home-section__inner">
                <div class="home-section__heading">
                    <p class="home-kicker">07 / ${page.copy().contact()}</p>
                    <h2 id="contact-title">${page.copy().contactTitle()}</h2>
                    <p>${page.copy().contactIntro()}</p>
                </div>
                <a class="home-contact__link" href="/contact">${page.copy().getInTouch()} <span aria-hidden="true">↗</span></a>
                <div class="home-section__foot"><span>${page.siteName()} / ${page.ownerAlias()}</span><span>${page.copy().journey()}</span></div>
            </div>
        </section>
    </main>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
