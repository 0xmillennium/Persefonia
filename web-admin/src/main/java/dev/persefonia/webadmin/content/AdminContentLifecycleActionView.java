package dev.persefonia.webadmin.content;

public record AdminContentLifecycleActionView(
        String publishAction,
        String unpublishAction,
        String archiveAction) {
    public boolean canPublish() {
        return publishAction != null;
    }

    public boolean canUnpublish() {
        return unpublishAction != null;
    }

    public boolean canArchive() {
        return archiveAction != null;
    }

    public boolean hasActions() {
        return canPublish() || canUnpublish() || canArchive();
    }

    public static AdminContentLifecycleActionView none() {
        return new AdminContentLifecycleActionView(null, null, null);
    }
}
