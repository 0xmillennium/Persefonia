@import dev.persefonia.webadmin.operations.AdminOperationsPage
@param AdminOperationsPage page
@template.admin.components.pageShell("Operations | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Operations</h1>

        <section>
            <h2>System status</h2>
            <dl>
                <dt>Application</dt><dd>${page.health().application().name()}</dd>
                <dt>Database</dt><dd>${page.health().database().name()}</dd>
                <dt>Redis</dt><dd>${page.health().redis().name()}</dd>
                <dt>Media Storage</dt><dd>${page.health().mediaStorage().name()}</dd>
                <dt>Cache purge provider</dt><dd>${page.health().cacheProvider().name()} — ${page.health().cacheProviderReadiness().name()}</dd>
                <dt>Database migrations</dt><dd>${page.health().migrations().status().name()}</dd>
                <dt>Current applied migration</dt><dd>${page.health().migrations().currentAppliedVersionOptional().orElse("Unknown")}</dd>
                <dt>Latest resolved migration</dt><dd>${page.health().migrations().latestResolvedVersionOptional().orElse("Unknown")}</dd>
                <dt>Pending migrations</dt><dd>${Integer.toString(page.health().migrations().pendingCount())}</dd>
            </dl>
            @if(page.health().redis().name().equals("DOWN"))
                <p>Contact rate limiting is unavailable.</p>
            @endif
        </section>

        <section>
            <h2>Recovery readiness</h2>
            <p>Review the PostgreSQL and durable Media recovery unit and run an explicit consistency verification.</p>
            <p><a href="/admin/operations/recovery">Open recovery diagnostics</a></p>
        </section>

        <section>
            <h2>Cache invalidation summary</h2>
            <dl>
                <dt>Requested</dt><dd>${Long.toString(page.summary().requested())}</dd>
                <dt>Running</dt><dd>${Long.toString(page.summary().running())}</dd>
                <dt>Stranded</dt><dd>${Long.toString(page.summary().stranded())}</dd>
                <dt>Retry available</dt><dd>${Long.toString(page.summary().retryAvailable())}</dd>
                <dt>Retry exhausted</dt><dd>${Long.toString(page.summary().retryExhausted())}</dd>
                <dt>Completed</dt><dd>${Long.toString(page.summary().completed())}</dd>
            </dl>
        </section>

        <section>
            <h2>Recent cache invalidations</h2>
            <form method="get" action="/admin/operations">
                <label for="status">Status</label>
                <select id="status" name="status">
                    <option value="" selected="${page.filter().value().isEmpty()}">All statuses</option>
                    @for(var status : dev.persefonia.platformoperations.domain.cache.CacheInvalidationStatus.values())
                        <option value="${status.name()}" selected="${page.filter().value().equals(status.name())}">${status.name()}</option>
                    @endfor
                </select>
                <label for="pageSize">Page size</label>
                <select id="pageSize" name="pageSize">
                    <option value="25" selected="${page.batches().pageSize() == 25}">25</option>
                    <option value="50" selected="${page.batches().pageSize() == 50}">50</option>
                    <option value="100" selected="${page.batches().pageSize() == 100}">100</option>
                </select>
                <button type="submit">Filter</button>
                <a href="/admin/operations">Clear filters</a>
            </form>

            @if(page.batches().items().isEmpty())
                <p>No cache invalidation batches found.</p>
            @else
                <table>
                    <thead><tr><th>Requested</th><th>Status</th><th>Attention</th><th>Targets</th><th>Attempts</th><th>Latest provider</th><th>Latest result</th><th>Failure reason</th><th>Details</th></tr></thead>
                    <tbody>
                    @for(var batch : page.batches().items())
                        <tr>
                            <td>${batch.requestedAt().toString()}</td>
                            <td>${batch.status().name()}</td>
                            <td>${batch.attentionState().name()}</td>
                            <td>${Integer.toString(batch.targetCount())}</td>
                            <td>${Integer.toString(batch.attemptCount())}</td>
                            <td>${batch.latestProviderOptional().map(Enum::name).orElse("")}</td>
                            <td>${batch.latestResultOptional().map(Enum::name).orElse("")}</td>
                            <td>${batch.latestFailureReasonOptional().map(Enum::name).orElse("")}</td>
                            <td><a href="/admin/operations/cache/${batch.id().value().toString()}">View</a></td>
                        </tr>
                    @endfor
                    </tbody>
                </table>
            @endif
            <nav aria-label="Cache invalidation pages">
                @if(page.hasPreviousPage())<a rel="prev" href="${page.previousUrl()}">Previous</a>@endif
                <span>Page ${Integer.toString(page.batches().page())} of ${Long.toString(page.totalPages())}</span>
                @if(page.hasNextPage())<a rel="next" href="${page.nextUrl()}">Next</a>@endif
            </nav>
        </section>
    </main>
`)
