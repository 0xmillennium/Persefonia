@import dev.persefonia.webadmin.AuthenticatedAdminView
@import dev.persefonia.webadmin.LogoutFormViewModel
@param AuthenticatedAdminView admin
@param LogoutFormViewModel logoutForm
@param boolean showRoles
<header>
    <p>Signed in as <strong>${admin.displayName()}</strong></p>
    @if(showRoles)
        <ul aria-label="Local admin roles">
            @for(String roleLabel : admin.roleLabels())
                <li>${roleLabel}</li>
            @endfor
        </ul>
    @endif
    <form method="post" action="${logoutForm.action()}">
        @template.admin.components.csrfField(logoutForm)
        <button type="submit">Logout</button>
    </form>
</header>
