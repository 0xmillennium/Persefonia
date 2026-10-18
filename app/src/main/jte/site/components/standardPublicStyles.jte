@param java.util.List<String> stylesheetPaths
@param String publicNavigationScriptPath = null
@template.site.components.stylesheetLinks(stylesheetPaths)
<link rel="stylesheet" href="/theme.css">
@if(publicNavigationScriptPath != null)
<script type="module" src="${publicNavigationScriptPath}"></script>
@endif
