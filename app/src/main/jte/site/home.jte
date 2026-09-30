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
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="homepage" lang="en">
        <div class="archive-stage" aria-hidden="true">
            <div class="archive-stage__folio" data-archive-folio>
                <span class="archive-stage__spine"></span>
                <span class="archive-stage__seam"></span>
                <span class="archive-stage__trace"></span>
                <span class="archive-stage__joint"></span>
                <span class="archive-stage__sheet archive-stage__sheet--a" data-archive-sheet="0"><span class="archive-stage__index">§ 01</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.i</span></span>
                <span class="archive-stage__sheet archive-stage__sheet--b" data-archive-sheet="1"><span class="archive-stage__index">§ 02</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.ii</span></span>
                <span class="archive-stage__sheet archive-stage__sheet--c" data-archive-sheet="2"><span class="archive-stage__index">§ 03</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.iii</span></span>
                <span class="archive-stage__sheet archive-stage__sheet--d" data-archive-sheet="3"><span class="archive-stage__index">§ 04</span><span class="archive-stage__rule"></span><span class="archive-stage__folio-number">f.iv</span></span>
                <span class="archive-stage__vellum"></span>
            </div>
        </div>
        <section class="home-section home-identity" id="identity" data-archive-chapter="0" aria-labelledby="identity-title">
            <div class="home-section__inner">
                <header class="home-nav">
                    <a class="home-nav__brand" href="/">${page.siteName()}</a>
                    <nav class="home-nav__links" aria-label="Primary">
                        <a href="#work">Projects</a><a href="#writing">Writing</a><a href="#research">Research</a><a href="#about">About</a>
                    </nav>
                    <details class="home-nav__mobile">
                        <summary>Menu</summary>
                        <nav aria-label="Mobile primary">
                            <a href="#work">Projects</a><a href="#writing">Writing</a><a href="#research">Research</a><a href="#about">About</a><a href="#contact">Contact</a>
                        </nav>
                    </details>
                </header>
                <div class="home-identity__copy" lang="en">
                    <p class="home-kicker">Software engineering <span>· Research · Writing</span></p>
                    <h1 id="identity-title">Building systems <em>worth explaining.</em></h1>
                    <p class="home-identity__intro">A personal space for software architecture, projects, research, writing, and the engineering decisions behind them.</p>
                    <div class="home-actions">
                        <a class="home-button" href="#work">Explore work <span aria-hidden="true">↗</span></a>
                        <a class="home-link" href="#writing">Read the latest writing <span aria-hidden="true">↗</span></a>
                    </div>
                </div>
                <div class="home-section__foot"><span>01 / 07&nbsp; Identity</span><span>by ${page.ownerAlias()}</span></div>
            </div>
        </section>
        <section class="home-section home-work" id="work" data-archive-chapter="1" aria-labelledby="work-title">
            <div class="home-section__inner">
                <div class="home-section__heading" lang="en">
                    <p class="home-kicker">02 / Selected work</p>
                    <h2 id="work-title">Work, in context.</h2>
                    <p>A few public projects, chosen for the questions and decisions behind them.</p>
                </div>
                @if(page.showFeaturedProjects() && !page.featuredProjects().isEmpty())
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
                                    <a class="home-link" href="${page.featuredProjects().get(i).publicUrl()}">Explore project <span aria-hidden="true">↗</span></a>
                                </article>
                            </li>
                        @endfor
                    </ol>
                @endif
                @if(page.showFeaturedProjects() && page.featuredProjects().isEmpty())
                    <p class="home-empty">Public projects will appear here when available.</p>
                @endif
                <a class="home-section__more" href="/${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}/projects">View all projects <span aria-hidden="true">↗</span></a>
                <div class="home-section__foot"><span>02 / 07&nbsp; Selected work</span></div>
            </div>
        </section>
        <section class="home-section home-writing" id="writing" data-archive-chapter="2" aria-labelledby="writing-title">
            <div class="home-section__inner">
                <div class="home-section__heading" lang="en">
                    <p class="home-kicker">03 / Writing</p>
                    <h2 id="writing-title">Thinking in public.</h2>
                    <p>Notes and essays make the reasoning visible after the work is done.</p>
                </div>
                @if(page.showLatestWriting() && !page.latestWriting().isEmpty())
                    <ol class="home-writing__list" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
                        @for(int i = 0; i < page.latestWriting().size(); i++)
                            <li><article>
                                <p class="home-writing__number">${i + 1} / Writing</p>
                                <h3><a href="${page.latestWriting().get(i).publicUrl()}">${page.latestWriting().get(i).title()}</a></h3>
                                <p>${page.latestWriting().get(i).summary()}</p>
                            </article></li>
                        @endfor
                    </ol>
                @endif
                @if(page.showLatestWriting() && page.latestWriting().isEmpty())
                    <p class="home-empty">Published writing will appear here when available.</p>
                @endif
                <div class="home-section__foot"><span>03 / 07&nbsp; Writing</span></div>
            </div>
        </section>
        <section class="home-section home-research" id="research" data-archive-chapter="3" aria-labelledby="research-title">
            <div class="home-section__inner">
                <div class="home-section__heading" lang="en">
                    <p class="home-kicker">04 / Research</p>
                    <h2 id="research-title">Questions in motion.</h2>
                    <p>Explorations connect observations, experiments, and open questions.</p>
                </div>
                @if(page.showResearchHighlights() && !page.researchHighlights().isEmpty())
                    <ol class="home-research__list" lang="${page.defaultLanguage().toLowerCase(java.util.Locale.ROOT)}">
                        @for(var research : page.researchHighlights())
                            <li><article><p class="home-project__meta">Research / Public record</p><h3><a href="${research.publicUrl()}">${research.title()}</a></h3><p>${research.summary()}</p></article></li>
                        @endfor
                    </ol>
                @endif
                @if(page.showResearchHighlights() && page.researchHighlights().isEmpty())
                    <p class="home-empty">Published research will appear here when available.</p>
                @endif
                <div class="home-section__foot"><span>04 / 07&nbsp; Research</span></div>
            </div>
        </section>
        <section class="home-section home-architecture" id="architecture" data-archive-chapter="4" aria-labelledby="architecture-title">
            <div class="home-section__inner">
                <div class="home-section__heading" lang="en">
                    <p class="home-kicker">05 / Architecture</p>
                    <h2 id="architecture-title">The system behind the work.</h2>
                    <p>The same fragments now form a legible structure: public experience, application decisions, domain boundaries, and durable state.</p>
                </div>
                <div class="home-architecture__structure" aria-label="Persefonia's public experience, application, domain, and durable state">
                    <div><span>01 / Public experience</span><strong>Pages, routes, and public access</strong></div>
                    <div><span>02 / Application</span><strong>Use cases and decisions</strong></div>
                    <div><span>03 / Domain</span><strong>Bounded contexts</strong></div>
                    <div><span>04 / Durable state</span><strong>PostgreSQL and media</strong></div>
                </div>
                <div class="home-section__foot"><span>05 / 07&nbsp; Architecture</span></div>
            </div>
        </section>
        <section class="home-section home-about" id="about" data-archive-chapter="5" aria-labelledby="about-title">
            <div class="home-section__inner">
                <div class="home-section__heading" lang="en">
                    <p class="home-kicker">06 / About</p>
                    <h2 id="about-title">Back to the person.</h2>
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
                            <div class="home-about__focus"><h3>Current focus</h3><ul>
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
                <div class="home-section__foot"><span>06 / 07&nbsp; About</span></div>
            </div>
        </section>
        <section class="home-section home-contact" id="contact" data-archive-chapter="6" aria-labelledby="contact-title">
            <div class="home-section__inner">
                <div class="home-section__heading" lang="en">
                    <p class="home-kicker">07 / Contact</p>
                    <h2 id="contact-title">Let’s continue the conversation.</h2>
                    <p>Questions, ideas, and thoughtful collaboration are welcome.</p>
                </div>
                <a class="home-contact__link" href="/contact">Get in touch <span aria-hidden="true">↗</span></a>
                <div class="home-section__foot"><span>${page.siteName()} / ${page.ownerAlias()}</span><span>Person → Work → Ideas → System → Person</span></div>
            </div>
        </section>
    </main>
    <script type="module" src="${page.scriptPath()}"></script>
</body>
</html>
