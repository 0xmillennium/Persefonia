@import dev.persefonia.webadmin.audit.AdminAuditListPage
@param AdminAuditListPage page
@template.admin.components.pageShell("Audit | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Audit history</h1>

        <form method="get" action="/admin/audit">
            <label for="action">Action</label>
            <input id="action" name="action" type="text" value="${page.filters().action()}" placeholder="content.published">

            <label for="actorType">Actor type</label>
            <select id="actorType" name="actorType">
                <option value="" selected="${page.filters().actorType().isEmpty()}">All actors</option>
                <option value="ADMIN" selected="${page.filters().actorType().equals("ADMIN")}">ADMIN</option>
                <option value="SYSTEM" selected="${page.filters().actorType().equals("SYSTEM")}">SYSTEM</option>
            </select>
            <label for="actorId">Actor UUID (ADMIN only)</label>
            <input id="actorId" name="actorId" type="text" value="${page.filters().actorId()}">

            <label for="entityContext">Context</label>
            <input id="entityContext" name="entityContext" type="text" value="${page.filters().entityContext()}" placeholder="publishing">
            <label for="entityType">Type</label>
            <input id="entityType" name="entityType" type="text" value="${page.filters().entityType()}" placeholder="content_item">
            <label for="entityId">Entity UUID</label>
            <input id="entityId" name="entityId" type="text" value="${page.filters().entityId()}">

            <label for="from">From (UTC instant)</label>
            <input id="from" name="from" type="text" value="${page.filters().from()}" placeholder="2026-09-03T17:00:00Z">
            <label for="to">To (exclusive UTC instant)</label>
            <input id="to" name="to" type="text" value="${page.filters().to()}" placeholder="2026-09-04T17:00:00Z">

            <label for="pageSize">Page size</label>
            <select id="pageSize" name="pageSize">
                <option value="25" selected="${page.records().pageSize() == 25}">25</option>
                <option value="50" selected="${page.records().pageSize() == 50}">50</option>
                <option value="100" selected="${page.records().pageSize() == 100}">100</option>
            </select>
            <button type="submit">Filter</button>
            <a href="/admin/audit">Clear filters</a>
        </form>

        @if(page.records().items().isEmpty())
            <p>No audit records yet.</p>
        @else
            <table>
                <thead><tr><th>Occurred</th><th>Action</th><th>Actor</th><th>Entity</th><th>Details</th></tr></thead>
                <tbody>
                @for(var record : page.records().items())
                    <tr>
                        <td>${record.occurredAt().toString()}</td>
                        <td>${record.action()}</td>
                        <td>${record.actorDisplay()} (${record.actorType()})</td>
                        <td>${record.entityContext()} / ${record.entityType()} / ${record.entityId().toString()}</td>
                        <td><a href="/admin/audit/${record.id().toString()}">View</a></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif

        <nav aria-label="Audit record pages">
            @if(page.hasPreviousPage())
                <a rel="prev" href="${page.previousUrl()}">Previous</a>
            @endif
            <span>Page ${Integer.toString(page.records().page())} of ${Long.toString(page.totalPages())}</span>
            @if(page.hasNextPage())
                <a rel="next" href="${page.nextUrl()}">Next</a>
            @endif
        </nav>
    </main>
`)
