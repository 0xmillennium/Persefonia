@param java.util.List<dev.persefonia.webpublic.navigation.PublicNavigationLink> navigationLinks = java.util.List.of()
<footer class="public-footer" lang="en">
    <a href="/">← 0xmillennium</a>
    <nav aria-label="More to explore"><a href="/search">Explore published work</a><a href="/contact">Get in touch ↗</a>@for(var link : navigationLinks)<a href="${link.url()}">${link.label()}</a>@endfor</nav>
</footer>
