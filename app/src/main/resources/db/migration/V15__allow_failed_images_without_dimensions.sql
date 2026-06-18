ALTER TABLE media.assets
    DROP CONSTRAINT ck_assets__image_dimensions;

ALTER TABLE media.assets
    ADD CONSTRAINT ck_assets__image_dimensions CHECK (
        kind <> 'IMAGE'
        OR processing_status <> 'PROCESSED'
        OR (image_width IS NOT NULL AND image_height IS NOT NULL)
    );
