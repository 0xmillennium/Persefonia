@import dev.persefonia.webadmin.AdminShellViewModel
@param AdminShellViewModel page
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${page.title()}</title>
</head>
<body>
    <main>
        <h1>${page.title()}</h1>
        <p>${page.message()}</p>
    </main>
</body>
</html>
