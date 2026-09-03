@import dev.persefonia.webadmin.audit.AdminAuditDetailPage
@param AdminAuditDetailPage page
@template.admin.components.pageShell("Audit record | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Audit record</h1>
        <p><a href="/admin/audit">Back to audit history</a></p>

        <section>
            <h2>Record</h2>
            <dl>
                <dt>ID</dt><dd>${page.record().id().toString()}</dd>
                <dt>Action</dt><dd>${page.record().action()}</dd>
                <dt>Occurred at</dt><dd>${page.record().occurredAt().toString()}</dd>
                <dt>Created at</dt><dd>${page.record().createdAt().toString()}</dd>
                @if(page.record().requestIdOptional().isPresent())
                    <dt>Request ID</dt><dd>${page.record().requestIdOptional().get()}</dd>
                @endif
            </dl>
        </section>

        <section>
            <h2>Actor</h2>
            <dl>
                <dt>Type</dt><dd>${page.record().actorType()}</dd>
                <dt>Display</dt><dd>${page.record().actorDisplay()}</dd>
                @if(page.record().actorType().equals("ADMIN"))
                    <dt>Context</dt><dd>${page.record().actorContext()}</dd>
                    <dt>Source type</dt><dd>${page.record().actorSourceType()}</dd>
                    <dt>Actor UUID</dt><dd>${page.record().actorId().toString()}</dd>
                @endif
            </dl>
        </section>

        <section>
            <h2>Entity</h2>
            <dl>
                <dt>Context</dt><dd>${page.record().entityContext()}</dd>
                <dt>Type</dt><dd>${page.record().entityType()}</dd>
                <dt>UUID</dt><dd>${page.record().entityId().toString()}</dd>
            </dl>
        </section>

        <section>
            <h2>Changes</h2>
            @if(page.record().changes().isEmpty())
                <p>No field changes recorded.</p>
            @else
                <table>
                    <thead><tr><th>Field</th><th>Old</th><th>New</th></tr></thead>
                    <tbody>
                    @for(var change : page.record().changes())
                        <tr>
                            <td>${change.fieldPath()}</td>
                            <td>${change.oldValueOptional().orElse("")}</td>
                            <td>${change.newValueOptional().orElse("")}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            @endif
        </section>

        <section>
            <h2>Metadata</h2>
            @if(page.record().metadata().isEmpty())
                <p>No metadata recorded.</p>
            @else
                <table>
                    <thead><tr><th>Key</th><th>Value</th></tr></thead>
                    <tbody>
                    @for(var entry : page.record().metadata())
                        <tr><td>${entry.key()}</td><td>${entry.value()}</td></tr>
                    @endfor
                    </tbody>
                </table>
            @endif
        </section>
    </main>
`)
