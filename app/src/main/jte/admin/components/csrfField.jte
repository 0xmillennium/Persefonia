@import dev.persefonia.webadmin.LogoutFormViewModel
@param LogoutFormViewModel form
<input type="hidden" name="${form.csrfParameterName()}" value="${form.csrfToken()}">
