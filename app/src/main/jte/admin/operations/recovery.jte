@import dev.persefonia.webadmin.operations.AdminOperationsRecoveryPage
@param AdminOperationsRecoveryPage page
@template.admin.components.pageShell("Recovery readiness | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Recovery readiness</h1>
        <p><a href="/admin/operations">Back to operations</a></p>

        <section>
            <h2>Application and schema compatibility</h2>
            <dl>
                <dt>Application</dt><dd>${page.context().release().applicationName()}</dd>
                <dt>Application version</dt><dd>${page.context().release().applicationVersion()}</dd>
                <dt>Current database migration</dt><dd>${page.context().migrations().currentAppliedVersionOptional().orElse("Unknown")}</dd>
                <dt>Latest resolved migration</dt><dd>${page.context().migrations().latestResolvedVersionOptional().orElse("Unknown")}</dd>
                <dt>Migration status</dt><dd>${page.context().migrations().status().name()}</dd>
                <dt>Media Storage</dt><dd>${page.context().mediaStorage().name()}</dd>
            </dl>
        </section>

        <section>
            <h2>Durable recovery unit</h2>
            <p>Per ADR 0020, the logical durable recovery unit is PostgreSQL business metadata together with durable Media storage. Redis, sessions, application caches, and edge/CDN caches are auxiliary and may start empty.</p>
            <p>This verification checks the currently mounted PostgreSQL and Media state for internal consistency. It does not prove that external backups were captured from the same recovery point.</p>
            <form method="post" action="/admin/operations/recovery/verify">
                @template.admin.components.csrfField(page.chrome().logoutForm())
                <button type="submit">Run deep verification</button>
            </form>
            <p>Deep verification runs only when explicitly requested. Refreshing this page does not rerun it.</p>
        </section>

        @if(page.report() == null)
            <section><h2>Deep verification</h2><p>Deep verification has not been run for this response.</p></section>
        @else
            <section>
                <h2>Verification result</h2>
                <dl>
                    <dt>Status</dt><dd>${page.report().status().name()}</dd>
                    <dt>Generated at</dt><dd>${page.report().generatedAt().toString()}</dd>
                    <dt>Media objects checked</dt><dd>${Long.toString(page.report().media().totalObjects())}</dd>
                    <dt>Media objects verified</dt><dd>${Long.toString(page.report().media().verifiedObjects())}</dd>
                    <dt>Unavailable objects</dt><dd>${Long.toString(page.report().media().unavailableObjects())}</dd>
                    <dt>Size mismatches</dt><dd>${Long.toString(page.report().media().sizeMismatchObjects())}</dd>
                    <dt>Checksum mismatches</dt><dd>${Long.toString(page.report().media().checksumMismatchObjects())}</dd>
                    <dt>Durable Asset references checked</dt><dd>${Long.toString(page.report().assetReferences().totalReferences())}</dd>
                    <dt>Dangling Asset references</dt><dd>${Long.toString(page.report().assetReferences().danglingReferences())}</dd>
                </dl>
            </section>

            @if(!page.report().media().reportedIssues().isEmpty())
                <section>
                    <h2>Media issues</h2>
                    <table><thead><tr><th>Category</th><th>Object kind</th><th>Asset ID</th><th>Variant</th></tr></thead><tbody>
                    @for(var issue : page.report().media().reportedIssues())
                        <tr><td>${issue.category().name()}</td><td>${issue.objectKind().name()}</td><td>${issue.assetId().toString()}</td><td>${issue.variantName() == null ? "" : issue.variantName()}</td></tr>
                    @endfor
                    </tbody></table>
                </section>
            @endif
            @if(page.report().media().reportedIssuesTruncated())
                <p>Additional Media issues were detected but omitted from this view.</p>
            @endif

            @if(!page.report().assetReferences().reportedIssues().isEmpty())
                <section>
                    <h2>Dangling durable Asset references</h2>
                    <table><thead><tr><th>Reference kind</th><th>Source entity ID</th><th>Missing Asset ID</th></tr></thead><tbody>
                    @for(var issue : page.report().assetReferences().reportedIssues())
                        <tr><td>${issue.referenceKind().name()}</td><td>${issue.sourceEntityId().toString()}</td><td>${issue.missingAssetId().toString()}</td></tr>
                    @endfor
                    </tbody></table>
                </section>
            @endif
            @if(page.report().assetReferences().reportedIssuesTruncated())
                <p>Additional durable Asset-reference issues were detected but omitted from this view.</p>
            @endif
        @endif
    </main>
`)
