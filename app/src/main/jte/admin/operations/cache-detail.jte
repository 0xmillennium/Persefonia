@import dev.persefonia.webadmin.operations.AdminOperationsDetailPage
@import dev.persefonia.platformoperations.application.operations.CacheRecoveryAction
@param AdminOperationsDetailPage page
@template.admin.components.pageShell("Cache invalidation | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Cache invalidation batch</h1>
        <p><a href="/admin/operations">Back to operations</a> · <a href="${page.auditUrl()}">Related audit history</a></p>

        <section>
            <h2>Batch</h2>
            <dl>
                <dt>ID</dt><dd>${page.batch().id().value().toString()}</dd>
                <dt>Reason</dt><dd>${page.batch().reason().name()}</dd>
                <dt>Requested by</dt><dd>${page.batch().requestedBy().name()}</dd>
                <dt>Requested at</dt><dd>${page.batch().requestedAt().toString()}</dd>
                <dt>Status</dt><dd>${page.batch().status().name()}</dd>
                <dt>Attention</dt><dd>${page.batch().attentionState().name()}</dd>
                @if(page.batch().runningSinceOptional().isPresent())
                    <dt>Running since</dt><dd>${page.batch().runningSinceOptional().get().toString()}</dd>
                @endif
                @if(page.batch().completedAtOptional().isPresent())
                    <dt>Completed at</dt><dd>${page.batch().completedAtOptional().get().toString()}</dd>
                @endif
                @if(page.batch().failureReasonOptional().isPresent())
                    <dt>Failure reason</dt><dd>${page.batch().failureReasonOptional().get().name()}</dd>
                @endif
                <dt>Version</dt><dd>${Long.toString(page.batch().version())}</dd>
            </dl>
        </section>

        <section>
            <h2>Recovery</h2>
            @if(page.batch().availableAction() == CacheRecoveryAction.EXECUTE_INITIAL)
                <p>This persisted request has not begun provider execution.</p>
                <form method="post" action="/admin/operations/cache/${page.batch().id().value().toString()}/execute">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Execute initial purge</button>
                </form>
            @elseif(page.batch().availableAction() == CacheRecoveryAction.RETRY_FAILED)
                <p>Only targets that failed in the previous attempt will be retried.</p>
                <form method="post" action="/admin/operations/cache/${page.batch().id().value().toString()}/retry">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Retry failed targets</button>
                </form>
            @elseif(page.batch().availableAction() == CacheRecoveryAction.RESUME_STRANDED)
                <p>The previous purge execution has no durable result and has exceeded the configured running threshold. Replaying the purge may repeat an external operation that already succeeded.</p>
                <form method="post" action="/admin/operations/cache/${page.batch().id().value().toString()}/resume">
                    @template.admin.components.csrfField(page.chrome().logoutForm())
                    <button type="submit">Resume stranded purge</button>
                </form>
            @elseif(page.batch().attentionState().name().equals("RETRY_EXHAUSTED"))
                <p>Retry budget exhausted.</p>
            @else
                <p>No recovery action is currently available.</p>
            @endif
        </section>

        <section>
            <h2>Targets</h2>
            <table>
                <thead><tr><th>Type</th><th>Target</th><th>Status</th></tr></thead>
                <tbody>
                @for(var target : page.batch().targets())
                    <tr><td>${target.type().name()}</td><td>${target.value().value()}</td><td>${target.status().name()}</td></tr>
                @endfor
                </tbody>
            </table>
        </section>

        <section>
            <h2>Attempts</h2>
            @if(page.batch().attempts().isEmpty())
                <p>No provider attempt has a durable result.</p>
            @else
                <table>
                    <thead><tr><th>Attempt</th><th>Provider</th><th>Attempted</th><th>Result</th><th>Failure reason</th></tr></thead>
                    <tbody>
                    @for(var attempt : page.batch().attempts())
                        <tr>
                            <td>${Integer.toString(attempt.attemptNumber())}</td>
                            <td>${attempt.provider().name()}</td>
                            <td>${attempt.attemptedAt().toString()}</td>
                            <td>${attempt.result().name()}</td>
                            <td>${attempt.failureReasonOptional().map(Enum::name).orElse("")}</td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            @endif
        </section>
    </main>
`)
