@import dev.persefonia.webpublic.contact.ContactPageViewModel
@param ContactPageViewModel page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
    <meta name="description" content="${page.description()}">
    @template.site.components.canonicalLink(page.canonicalUrl())
    <meta name="robots" content="noindex, follow">
    @template.site.components.openGraphTags("website", page.title(), page.description(), page.canonicalUrl())
    @template.site.components.stylesheetLinks(page.stylesheetPaths())
    @template.site.components.feedDiscoveryLink()
</head>
<body>
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <main id="main-content" class="public-page">
        <header>
            <h1>Contact</h1>
        </header>
        @if(page.hasMessage())
            <p role="${page.submitted() ? "status" : "alert"}">${page.message()}</p>
        @endif
        @if(page.hasErrors())
            <div role="alert" aria-labelledby="contact-errors-title">
                <h2 id="contact-errors-title">Please correct the highlighted fields.</h2>
            </div>
        @endif
        <form method="post" action="/contact">
            <input type="hidden" name="${page.csrfToken().getParameterName()}" value="${page.csrfToken().getToken()}">

            <div>
                <label for="senderName">Name</label>
                @if(page.hasFieldError("senderName"))
                    <input id="senderName" name="senderName" type="text" value="${page.form().senderName()}" autocomplete="name" aria-describedby="senderName-error">
                    <p id="senderName-error" role="alert">${page.fieldError("senderName")}</p>
                @else
                    <input id="senderName" name="senderName" type="text" value="${page.form().senderName()}" autocomplete="name">
                @endif
            </div>

            <div>
                <label for="senderEmail">Email</label>
                @if(page.hasFieldError("senderEmail"))
                    <input id="senderEmail" name="senderEmail" type="email" value="${page.form().senderEmail()}" autocomplete="email" aria-describedby="senderEmail-error">
                    <p id="senderEmail-error" role="alert">${page.fieldError("senderEmail")}</p>
                @else
                    <input id="senderEmail" name="senderEmail" type="email" value="${page.form().senderEmail()}" autocomplete="email">
                @endif
            </div>

            <div>
                <label for="subject">Subject</label>
                @if(page.hasFieldError("subject"))
                    <input id="subject" name="subject" type="text" value="${page.form().subject()}" aria-describedby="subject-error">
                    <p id="subject-error" role="alert">${page.fieldError("subject")}</p>
                @else
                    <input id="subject" name="subject" type="text" value="${page.form().subject()}">
                @endif
            </div>

            <div>
                <label for="body">Message</label>
                @if(page.hasFieldError("body"))
                    <textarea id="body" name="body" rows="8" aria-describedby="body-error">${page.form().body()}</textarea>
                    <p id="body-error" role="alert">${page.fieldError("body")}</p>
                @else
                    <textarea id="body" name="body" rows="8">${page.form().body()}</textarea>
                @endif
            </div>

            <button type="submit">Send message</button>
        </form>
    </main>
</body>
</html>
