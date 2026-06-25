@import dev.persefonia.webadmin.contact.AdminContactDetailPage
@param AdminContactDetailPage page
@template.admin.components.pageShell("Contact message | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>${page.message().subject()}</h1>
        <p><a href="/admin/contact">Back to contact inbox</a></p>
        @if(page.hasFlashMessage())
            <p role="status">${page.flashMessage()}</p>
        @endif
        @if(page.hasErrorMessage())
            <p role="alert">${page.errorMessage()}</p>
        @endif

        <section>
            <h2>Message</h2>
            <dl>
                <dt>Sender name</dt><dd>${page.message().senderName()}</dd>
                <dt>Sender email</dt><dd>${page.message().senderEmail()}</dd>
                <dt>Subject</dt><dd>${page.message().subject()}</dd>
                <dt>Status</dt><dd>${page.message().status().name()}</dd>
                <dt>Mail delivery</dt><dd>${page.message().mailDeliveryStatus().name()}</dd>
                <dt>Submitted</dt><dd>${page.message().submittedAt().toString()}</dd>
                <dt>Updated</dt><dd>${page.message().updatedAt().toString()}</dd>
            </dl>
            <pre>${page.message().body()}</pre>
        </section>

        <section>
            <h2>Status actions</h2>
            @if(page.statusActions().isEmpty())
                <p>No status actions available.</p>
            @else
                @for(var action : page.statusActions())
                    <form method="post" action="${action.actionPath()}">
                        @template.admin.components.csrfField(page.chrome().logoutForm())
                        <button type="submit">${action.label()}</button>
                    </form>
                @endfor
            @endif
        </section>

        <section>
            <h2>Mail attempts</h2>
            @if(page.message().mailAttempts().isEmpty())
                <p>No mail attempts.</p>
            @else
                <table>
                    <thead><tr><th>Attempted</th><th>Result</th><th>Failure reason</th></tr></thead>
                    <tbody>
                    @for(var attempt : page.message().mailAttempts())
                        <tr>
                            <td>${attempt.attemptedAt().toString()}</td>
                            <td>${attempt.result().name()}</td>
                            @if(attempt.failureReasonOptional().isPresent())
                                <td>${attempt.failureReasonOptional().get()}</td>
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
            <h2>Status changes</h2>
            @if(page.message().statusChanges().isEmpty())
                <p>No status changes.</p>
            @else
                <table>
                    <thead><tr><th>Changed</th><th>Previous</th><th>New</th><th>Changed by</th></tr></thead>
                    <tbody>
                    @for(var change : page.message().statusChanges())
                        <tr>
                            <td>${change.changedAt().toString()}</td>
                            <td>${change.previousStatus().name()}</td>
                            <td>${change.newStatus().name()}</td>
                            <td>${change.changedBy().value().toString()}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            @endif
        </section>
    </main>
`)
