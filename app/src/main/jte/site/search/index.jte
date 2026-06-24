@import dev.persefonia.webpublic.search.PublicSearchPage
@import dev.persefonia.webpublic.search.PublicSearchResultItem
@param PublicSearchPage page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Search</title>
    <meta name="description" content="Search public content and projects.">
    @template.site.components.canonicalLink(page.canonicalUrl())
    <meta name="robots" content="noindex, follow">
    <meta property="og:title" content="Search">
    <meta property="og:description" content="Search public content and projects.">
    <meta property="og:url" content="${page.canonicalUrl()}">
    <meta property="og:type" content="website">
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
</head>
<body>
    <main class="public-page">
        <header>
            <h1>Search</h1>
        </header>
        <form action="/search" method="get" role="search">
            <label for="q">Search query</label>
            <input id="q" name="q" type="search" value="${page.query()}" autocomplete="off">
            <button type="submit">Search</button>
        </form>
        @if(page.hasValidationError())
            <p role="alert">${page.validationError()}</p>
        @endif
        @if(page.hasSearched())
            @if(page.totalCount() == 1)
                <p>1 result for ${page.normalizedQuery()}</p>
            @else
                <p>${page.totalCount()} results for ${page.normalizedQuery()}</p>
            @endif
            @if(page.hasResults())
                <ol>
                    @for(PublicSearchResultItem result : page.results())
                        <li>
                            <article>
                                <h2><a href="${result.publicUrl()}">${result.title()}</a></h2>
                                <p>${result.summary()}</p>
                                <p>
                                    <span>${result.resourceType()}</span>
                                    <span>${result.languageLabel()}</span>
                                </p>
                            </article>
                        </li>
                    @endfor
                </ol>
                @if(page.hasPreviousPage() || page.hasNextPage())
                    <nav aria-label="Search result pages">
                        @if(page.hasPreviousPage())
                            <a href="${page.previousPageUrl()}" rel="prev">Previous</a>
                        @endif
                        <span>Page ${page.currentPage()} of ${page.totalPages()}</span>
                        @if(page.hasNextPage())
                            <a href="${page.nextPageUrl()}" rel="next">Next</a>
                        @endif
                    </nav>
                @endif
            @else
                <p>No matching public results were found.</p>
            @endif
        @endif
    </main>
</body>
</html>
