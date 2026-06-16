@import dev.persefonia.webpublic.content.PublicContentHreflangLinkView
@param java.util.List<PublicContentHreflangLinkView> links
@for(PublicContentHreflangLinkView link : links)
    <link rel="alternate" hreflang="${link.languageCode()}" href="${link.href()}">
@endfor
