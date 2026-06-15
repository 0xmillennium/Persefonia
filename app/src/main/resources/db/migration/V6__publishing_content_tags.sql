CREATE TABLE publishing.content_item_tags (
    content_item_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    assigned_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_content_item_tags PRIMARY KEY (content_item_id, tag_id),
    CONSTRAINT fk_content_item_tags__content_items
        FOREIGN KEY (content_item_id)
        REFERENCES publishing.content_items (id)
        ON DELETE CASCADE
);

CREATE INDEX ix_content_item_tags_tag_id
    ON publishing.content_item_tags (tag_id);
