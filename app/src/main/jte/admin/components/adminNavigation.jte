@import dev.persefonia.webadmin.AdminNavigationItem
@param java.util.List<AdminNavigationItem> navigation
<nav aria-label="Admin navigation">
    <ul>
        @for(var item : navigation)
            <li>
                @if(item.disabled())
                    <span aria-disabled="true">${item.label()}</span>
                @else
                    <a href="${item.href()}" aria-current="${item.active() ? "page" : null}">${item.label()}</a>
                @endif
            </li>
        @endfor
    </ul>
</nav>
