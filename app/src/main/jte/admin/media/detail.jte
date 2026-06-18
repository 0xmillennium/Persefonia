@import dev.persefonia.medialibrary.domain.asset.AssetKind
@import dev.persefonia.webadmin.media.AdminMediaDetailPage
@param AdminMediaDetailPage page
@template.admin.components.pageShell("Media detail | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>${page.asset().summary().originalFilename()}</h1>
        <p><a href="/admin/media">Back to media</a></p>
        @if(page.flashMessage() != null)
            <p role="status">${page.flashMessage().message()}</p>
        @endif
        @if(page.hasErrors())
            @for(var error : page.globalErrors())
                <p role="alert">${error}</p>
            @endfor
            @for(var error : page.fieldErrors())
                <p role="alert">${error.field()}: ${error.message()}</p>
            @endfor
        @endif

        <section>
            <h2>Asset metadata</h2>
            <dl>
                <dt>Asset ID</dt><dd>${page.asset().summary().assetId().value().toString()}</dd>
                <dt>Kind</dt><dd>${page.asset().summary().kind().name()}</dd>
                <dt>Visibility</dt><dd>${page.asset().summary().visibility().name()}</dd>
                <dt>Processing</dt><dd>${page.asset().summary().processingStatus().name()}</dd>
                <dt>Content type</dt><dd>${page.asset().summary().contentType()}</dd>
                <dt>Extension</dt><dd>${page.asset().summary().fileExtension()}</dd>
                <dt>Size</dt><dd>${Long.toString(page.asset().summary().sizeBytes())}</dd>
                <dt>Checksum</dt><dd>${page.asset().summary().shortChecksum()}</dd>
                @if(!page.asset().summary().dimensionsLabel().isBlank())
                    <dt>Dimensions</dt><dd>${page.asset().summary().dimensionsLabel()}</dd>
                @endif
                <dt>Created</dt><dd>${page.asset().summary().createdAt().toString()}</dd>
                <dt>Updated</dt><dd>${page.asset().summary().updatedAt().toString()}</dd>
            </dl>
        </section>

        <section>
            <h2>Edit metadata</h2>
            <form method="post" action="/admin/media/${page.asset().summary().assetId().value().toString()}">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <label for="visibility">Visibility</label>
                <select id="visibility" name="visibility">
                    <option value="PRIVATE" selected="${"PRIVATE".equals(page.form().getVisibility())}">Private</option>
                    <option value="PUBLIC" selected="${"PUBLIC".equals(page.form().getVisibility())}">Public</option>
                </select>
                @if(page.asset().summary().kind() == AssetKind.IMAGE)
                    <label for="altText">Alt text</label>
                    <input id="altText" name="altText" maxlength="500" value="${page.form().getAltText()}">
                    <label for="decorative">
                        <input id="decorative" name="decorative" type="checkbox" checked="${page.form().isDecorative()}">
                        Decorative image
                    </label>
                @endif
                <button type="submit">Save metadata</button>
            </form>
        </section>

        <section>
            <h2>Variants</h2>
            @if(page.asset().variants().isEmpty())
                <p>No variants.</p>
            @else
                <table>
                    <thead><tr><th>Name</th><th>Dimensions</th><th>Type</th><th>Size</th><th>Checksum</th><th>Public route</th></tr></thead>
                    <tbody>
                    @for(var variant : page.asset().variants())
                        <tr>
                            <td>${variant.name()}</td>
                            <td>${Integer.toString(variant.width())} x ${Integer.toString(variant.height())}</td>
                            <td>${variant.contentType()}</td>
                            <td>${Long.toString(variant.sizeBytes())}</td>
                            <td>${variant.checksum()}</td>
                            @if(variant.publicRouteOptional().isPresent())
                                <td>${variant.publicRouteOptional().get()}</td>
                            @else
                                <td></td>
                            @endif
                        </tr>
                    @endfor
                    </tbody>
                </table>
            @endif
        </section>

        <section>
            <h2>Validation results</h2>
            @if(page.asset().validationResults().isEmpty())
                <p>No validation results.</p>
            @else
                <table>
                    <thead><tr><th>Rule</th><th>Status</th><th>Message</th><th>Checked</th></tr></thead>
                    <tbody>
                    @for(var result : page.asset().validationResults())
                        <tr>
                            <td>${result.rule()}</td>
                            <td>${result.status()}</td>
                            @if(result.message() != null)
                                <td>${result.message()}</td>
                            @else
                                <td></td>
                            @endif
                            <td>${result.checkedAt().toString()}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            @endif
        </section>
    </main>
`)
