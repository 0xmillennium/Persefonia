@param String projectsUrl = "/en/projects"
@param String current = ""
<header class="public-header" lang="en">
    <a class="public-brand" href="/" aria-label="0xmillennium home"><img src="/assets/identity-mark.svg" width="30" height="40" alt=""><span>0XMILLENNIUM</span></a>
    <nav aria-label="Primary navigation">
        <a href="${projectsUrl}" aria-current="${current.equals("projects") ? "page" : null}">Projects</a>
        <a href="/search" aria-current="${current.equals("search") ? "page" : null}">Writing &amp; search</a>
        <a href="/contact" aria-current="${current.equals("contact") ? "page" : null}">Contact</a>
    </nav>
</header>
