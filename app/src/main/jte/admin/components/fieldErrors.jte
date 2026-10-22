@import dev.persefonia.webadmin.FieldError
@import dev.persefonia.webadmin.FieldErrors
@param java.util.List<? extends FieldError> errors
@if(!errors.isEmpty())
    <section class="field-errors" role="alert" tabindex="-1" autofocus aria-labelledby="field-errors-title">
        <h2 id="field-errors-title">Please correct the following fields</h2>
        <ul>
            @for(String field : FieldErrors.fields(errors))
                <li id="error-${field}"><a href="#${field}">${FieldErrors.message(errors, field)}</a></li>
            @endfor
        </ul>
    </section>
@endif
