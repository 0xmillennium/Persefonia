ALTER TABLE portfolio.site_presentation_settings
    RENAME COLUMN default_theme TO standard_public_theme;
ALTER TABLE portfolio.site_presentation_settings
    RENAME COLUMN show_featured_projects TO promote_featured_projects;
ALTER TABLE portfolio.site_presentation_settings
    RENAME COLUMN show_latest_writing TO promote_latest_writing;
ALTER TABLE portfolio.site_presentation_settings
    RENAME COLUMN show_research_highlights TO promote_research_highlights;

ALTER TABLE portfolio.site_presentation_settings
    ADD COLUMN research_highlight_limit integer NOT NULL DEFAULT 3;

UPDATE portfolio.site_presentation_settings
SET featured_project_limit = LEAST(3, GREATEST(1, featured_project_limit)),
    latest_writing_limit = LEAST(3, GREATEST(1, latest_writing_limit));

ALTER TABLE portfolio.site_presentation_settings
    DROP CONSTRAINT ck_site_presentation_settings__default_theme,
    DROP CONSTRAINT ck_site_presentation_settings__featured_project_limit_positive,
    DROP CONSTRAINT ck_site_presentation_settings__latest_writing_limit_positive;

ALTER TABLE portfolio.site_presentation_settings
    ADD CONSTRAINT ck_site_presentation_settings__standard_public_theme
        CHECK (standard_public_theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    ADD CONSTRAINT ck_site_presentation_settings__featured_project_limit_range
        CHECK (featured_project_limit BETWEEN 1 AND 3),
    ADD CONSTRAINT ck_site_presentation_settings__latest_writing_limit_range
        CHECK (latest_writing_limit BETWEEN 1 AND 3),
    ADD CONSTRAINT ck_site_presentation_settings__research_highlight_limit_range
        CHECK (research_highlight_limit BETWEEN 1 AND 3);
