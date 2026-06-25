@import dev.persefonia.webadmin.contact.AdminContactListPage
@param AdminContactListPage page
@template.admin.components.pageShell("Contact inbox | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Contact inbox</h1>

        <form method="get" action="/admin/contact">
            <label for="status">Status</label>
            <select id="status" name="status">
                @for(var status : page.statusOptions())
                    <option value="${status}" selected="${page.selected(status)}">${status}</option>
                @endfor
            </select>
            <button type="submit">Filter</button>
        </form>

        @if(page.messages().items().isEmpty())
            <p>No contact messages yet.</p>
        @else
            <table>
                <thead>
                    <tr>
                        <th>Submitted</th>
                        <th>Sender name</th>
                        <th>Sender email</th>
                        <th>Subject</th>
                        <th>Status</th>
                        <th>Mail delivery</th>
                        <th>Mail attempts</th>
                        <th>Latest mail attempt</th>
                        <th>Updated</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                @for(var message : page.messages().items())
                    <tr>
                        <td>${message.submittedAt().toString()}</td>
                        <td>${message.senderName()}</td>
                        <td>${message.senderEmail()}</td>
                        <td>${message.subject()}</td>
                        <td>${message.status().name()}</td>
                        <td>${message.mailDeliveryStatus().name()}</td>
                        <td>${Long.toString(message.mailAttemptCount())}</td>
                        @if(message.latestMailAttemptResultOptional().isPresent())
                            <td>${message.latestMailAttemptResultOptional().get().name()}</td>
                        @else
                            <td></td>
                        @endif
                        <td>${message.updatedAt().toString()}</td>
                        <td><a href="/admin/contact/${message.id().value().toString()}">View</a></td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif

        <nav aria-label="Contact message pages">
            @if(page.hasPreviousPage())
                <a rel="prev" href="/admin/contact?status=${page.selectedStatus()}&page=${Integer.toString(page.previousPage())}&pageSize=${Integer.toString(page.messages().pageSize())}">Previous</a>
            @endif
            <span>Page ${Integer.toString(page.messages().page())} of ${Long.toString(page.totalPages())}</span>
            @if(page.hasNextPage())
                <a rel="next" href="/admin/contact?status=${page.selectedStatus()}&page=${Integer.toString(page.nextPage())}&pageSize=${Integer.toString(page.messages().pageSize())}">Next</a>
            @endif
        </nav>
    </main>
`)
