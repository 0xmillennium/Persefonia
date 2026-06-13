@import dev.persefonia.webpublic.content.PublicContentHeadingView
@import dev.persefonia.webpublic.content.PublicContentPage
@param PublicContentPage page
<!doctype html>
<html lang="${page.language()}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.seoTitle().orElse(page.title())}</title>
    @if(page.seoDescription().isPresent())
        <meta name="description" content="${page.seoDescription().get()}">
    @endif
    @if(page.openGraphTitle().isPresent())
        <meta property="og:title" content="${page.openGraphTitle().get()}">
    @endif
    @if(page.openGraphDescription().isPresent())
        <meta property="og:description" content="${page.openGraphDescription().get()}">
    @endif
    <link rel="canonical" href="${page.canonicalPath()}">
</head>
<body>
    <main>
        <article>
            <header>
                <p>${page.typeLabel()}</p>
                <h1>${page.title()}</h1>
                <p>${page.summary()}</p>
                <p>
                    <time datetime="${page.publishedAt().toString()}">${page.publishedAt().toString()}</time>
                    <span>${page.readingTime()} min read</span>
                </p>
            </header>
            @if(!page.headings().isEmpty())
                <nav aria-label="Table of contents">
                    <ol>
                        @for(PublicContentHeadingView heading : page.headings())
                            <li>
                                <a href="#${heading.anchor()}">${heading.text()}</a>
                            </li>
                        @endfor
                    </ol>
                </nav>
            @endif
            <section>
                $unsafe{page.renderedHtml()}
            </section>
        </article>
    </main>
</body>
</html>
