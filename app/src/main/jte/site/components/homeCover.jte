@import dev.persefonia.webpublic.PublicHomeCoverView
@param PublicHomeCoverView cover
@param String sizes
<img src="${cover.image().url()}" srcset="${cover.sourceSet()}" sizes="auto, ${sizes}" alt="" width="${cover.image().width()}" height="${cover.image().height()}" loading="lazy" decoding="async">
