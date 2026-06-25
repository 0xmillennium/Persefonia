@import dev.persefonia.webadmin.analytics.AdminAnalyticsPage
@param AdminAnalyticsPage page
@template.admin.components.pageShell("Analytics | Persefonia Admin", page.chrome().admin(), page.chrome().navigation(), page.chrome().logoutForm(), false, content = @`
    <main>
        <h1>Analytics</h1>
        <p>Privacy-safe aggregate activity. Totals are counted per metric and surface only; no visitor, path, or search details are recorded.</p>

        @if(page.summary().isEmpty())
            <p>No aggregate activity has been recorded yet.</p>
        @else
            <dl>
                <dt>Today</dt>
                <dd>${Long.toString(page.summary().totalTodayCount())}</dd>
                <dt>Last 7 days</dt>
                <dd>${Long.toString(page.summary().totalLastSevenDaysCount())}</dd>
                <dt>Last 30 days</dt>
                <dd>${Long.toString(page.summary().totalLastThirtyDaysCount())}</dd>
                <dt>All time</dt>
                <dd>${Long.toString(page.summary().totalAllTimeCount())}</dd>
            </dl>

            <table>
                <thead>
                    <tr>
                        <th>Metric</th>
                        <th>Surface</th>
                        <th>Today</th>
                        <th>Last 7 days</th>
                        <th>Last 30 days</th>
                        <th>All time</th>
                    </tr>
                </thead>
                <tbody>
                @for(var row : page.summary().rows())
                    <tr>
                        <td>${row.metric().name()}</td>
                        <td>${row.surface().name()}</td>
                        <td>${Long.toString(row.todayCount())}</td>
                        <td>${Long.toString(row.lastSevenDaysCount())}</td>
                        <td>${Long.toString(row.lastThirtyDaysCount())}</td>
                        <td>${Long.toString(row.allTimeCount())}</td>
                    </tr>
                @endfor
                </tbody>
            </table>
        @endif

        <p>Reporting windows end on ${page.summary().range().today().toString()}.</p>
    </main>
`)
