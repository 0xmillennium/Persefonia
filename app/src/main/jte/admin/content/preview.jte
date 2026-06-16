@import dev.persefonia.webadmin.content.AdminContentPreviewPage
@param AdminContentPreviewPage page
@template.admin.components.pageShell(page.heading() + " | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
<main>
    <h1>${page.heading()}</h1>
    <p><a href="${page.editLink()}">Back to edit</a></p>
    @for(var error : page.globalErrors())
        <p role="alert">${error}</p>
    @endfor
    @if(page.available())
        @if(page.containsMermaid())
            <p>Mermaid blocks are shown as code and are not executed.</p>
        @endif
        <article>$unsafe{page.sanitizedHtml()}</article>
    @endif
</main>
`)
