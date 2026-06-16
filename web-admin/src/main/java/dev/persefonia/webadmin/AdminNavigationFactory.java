package dev.persefonia.webadmin;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class AdminNavigationFactory {
    public List<AdminNavigationItem> create(AdminNavigationSection activeSection) {
        Objects.requireNonNull(activeSection, "activeSection");
        return List.of(
                link(AdminNavigationSection.DASHBOARD, activeSection, "Dashboard", "/admin"),
                link(AdminNavigationSection.CONTENT, activeSection, "Content", "/admin/content"),
                link(AdminNavigationSection.SERIES, activeSection, "Series", "/admin/series"),
                link(AdminNavigationSection.TAGS, activeSection, "Tags", "/admin/tags"),
                link(AdminNavigationSection.REDIRECTS, activeSection, "Redirects", "/admin/discovery/redirects"),
                link(AdminNavigationSection.PROFILE, activeSection, "Profile", "/admin/profile"),
                AdminNavigationItem.disabled("Projects"),
                AdminNavigationItem.disabled("Media"),
                AdminNavigationItem.disabled("Contact"),
                AdminNavigationItem.disabled("Analytics"),
                AdminNavigationItem.disabled("Audit"),
                link(AdminNavigationSection.SETTINGS, activeSection, "Settings", "/admin/settings/site"));
    }

    private static AdminNavigationItem link(
            AdminNavigationSection section,
            AdminNavigationSection activeSection,
            String label,
            String href) {
        return section == activeSection
                ? AdminNavigationItem.activeLink(label, href)
                : AdminNavigationItem.link(label, href);
    }
}
