@import dev.persefonia.webadmin.media.AdminMediaListPage
@param AdminMediaListPage page
@param java.util.List<String> adminStylesheetPaths = java.util.List.of()
@template.admin.components.pageShell("Media | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, adminStylesheetPaths = adminStylesheetPaths, content = @`
    <main id="admin-content" tabindex="-1">
        <h1>Media</h1>
        <p><a href="/admin/media/new">Upload media</a></p>
        @if(page.flashMessage() != null)
            <p role="status">${page.flashMessage().message()}</p>
        @endif
        @if(page.assets().isEmpty())
            <p>No media assets yet.</p>
        @else
            <div class="admin-table-scroll" role="region" aria-label="Scrollable table" tabindex="0"><table>
                <thead>
                    <tr>
                        <th>Filename</th>
                        <th>Kind</th>
                        <th>Visibility</th>
                        <th>Processing</th>
                        <th>Type</th>
                        <th>Size</th>
                        <th>Dimensions</th>
                        <th>Updated</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                @for(var asset : page.assets())
                    <tr>
                        <td>${asset.originalFilename()}</td>
                        <td>${asset.kind().name()}</td>
                        <td>${asset.visibility().name()}</td>
                        <td>${asset.processingStatus().name()}</td>
                        <td>${asset.contentType()}</td>
                        <td>${Long.toString(asset.sizeBytes())}</td>
                        <td>${asset.dimensionsLabel()}</td>
                        <td>${asset.updatedAt().toString()}</td>
                        <td><a href="/admin/media/${asset.assetId().value().toString()}">View/edit</a></td>
                    </tr>
                @endfor
                </tbody>
            </table></div>
        @endif
    </main>
`)
