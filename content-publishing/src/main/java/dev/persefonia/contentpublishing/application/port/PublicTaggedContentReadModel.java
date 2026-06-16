package dev.persefonia.contentpublishing.application.port;

import dev.persefonia.contentpublishing.application.query.PublicTaggedContentItem;
import dev.persefonia.contentpublishing.application.query.PublicTaggedContentQuery;
import java.util.List;

public interface PublicTaggedContentReadModel {
    List<PublicTaggedContentItem> list(PublicTaggedContentQuery query);
}
