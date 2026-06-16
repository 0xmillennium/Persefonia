CREATE TABLE portfolio.site_presentation_settings (
    id uuid NOT NULL,
    singleton_key boolean NOT NULL DEFAULT true,
    site_name text NOT NULL,
    default_language text NOT NULL,
    title_suffix text NULL,
    default_meta_description text NULL,
    default_og_image_asset_id uuid NULL,
    default_theme text NOT NULL,
    show_featured_projects boolean NOT NULL,
    show_latest_writing boolean NOT NULL,
    show_research_highlights boolean NOT NULL,
    featured_project_limit integer NOT NULL,
    latest_writing_limit integer NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_site_presentation_settings PRIMARY KEY (id),
    CONSTRAINT uq_site_presentation_settings__singleton_key UNIQUE (singleton_key),
    CONSTRAINT ck_site_presentation_settings__singleton_key_true CHECK (singleton_key = true),
    CONSTRAINT ck_site_presentation_settings__site_name_not_blank CHECK (btrim(site_name) <> ''),
    CONSTRAINT ck_site_presentation_settings__default_language CHECK (default_language IN ('TR', 'EN')),
    CONSTRAINT ck_site_presentation_settings__title_suffix_not_blank CHECK (title_suffix IS NULL OR btrim(title_suffix) <> ''),
    CONSTRAINT ck_site_presentation_settings__default_meta_description_not_blank CHECK (default_meta_description IS NULL OR btrim(default_meta_description) <> ''),
    CONSTRAINT ck_site_presentation_settings__default_theme CHECK (default_theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT ck_site_presentation_settings__featured_project_limit_positive CHECK (featured_project_limit > 0),
    CONSTRAINT ck_site_presentation_settings__latest_writing_limit_positive CHECK (latest_writing_limit > 0),
    CONSTRAINT ck_site_presentation_settings__version_non_negative CHECK (version >= 0)
);

CREATE TABLE portfolio.site_supported_languages (
    settings_id uuid NOT NULL,
    language text NOT NULL,
    CONSTRAINT pk_site_supported_languages PRIMARY KEY (settings_id, language),
    CONSTRAINT fk_site_supported_languages__settings
        FOREIGN KEY (settings_id)
        REFERENCES portfolio.site_presentation_settings (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_site_supported_languages__language CHECK (language IN ('TR', 'EN'))
);

CREATE TABLE portfolio.personal_profiles (
    id uuid NOT NULL,
    display_name text NOT NULL,
    active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_personal_profiles PRIMARY KEY (id),
    CONSTRAINT ck_personal_profiles__display_name_not_blank CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_personal_profiles__updated_not_before_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_personal_profiles__version_non_negative CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_personal_profiles__one_active
    ON portfolio.personal_profiles (active)
    WHERE active = true;

CREATE TABLE portfolio.profile_localizations (
    id uuid NOT NULL,
    profile_id uuid NOT NULL,
    language text NOT NULL,
    short_bio text NOT NULL,
    long_bio text NOT NULL,
    location_text text NULL,
    CONSTRAINT pk_profile_localizations PRIMARY KEY (id),
    CONSTRAINT fk_profile_localizations__profile
        FOREIGN KEY (profile_id)
        REFERENCES portfolio.personal_profiles (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_profile_localizations__profile_language UNIQUE (profile_id, language),
    CONSTRAINT ck_profile_localizations__language CHECK (language IN ('TR', 'EN')),
    CONSTRAINT ck_profile_localizations__short_bio_not_blank CHECK (btrim(short_bio) <> ''),
    CONSTRAINT ck_profile_localizations__long_bio_not_blank CHECK (btrim(long_bio) <> ''),
    CONSTRAINT ck_profile_localizations__location_text_not_blank CHECK (location_text IS NULL OR btrim(location_text) <> '')
);

CREATE INDEX ix_profile_localizations__profile_id
    ON portfolio.profile_localizations (profile_id);

CREATE INDEX ix_profile_localizations__language
    ON portfolio.profile_localizations (language);

CREATE TABLE portfolio.external_profile_links (
    id uuid NOT NULL,
    profile_id uuid NOT NULL,
    label text NOT NULL,
    url text NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_external_profile_links PRIMARY KEY (id),
    CONSTRAINT fk_external_profile_links__profile
        FOREIGN KEY (profile_id)
        REFERENCES portfolio.personal_profiles (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_external_profile_links__profile_sort_order UNIQUE (profile_id, sort_order),
    CONSTRAINT ck_external_profile_links__label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT ck_external_profile_links__url_not_blank CHECK (btrim(url) <> ''),
    CONSTRAINT ck_external_profile_links__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_external_profile_links__profile_id
    ON portfolio.external_profile_links (profile_id);

CREATE TABLE portfolio.technical_focus_areas (
    id uuid NOT NULL,
    profile_localization_id uuid NOT NULL,
    name text NOT NULL,
    description text NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_technical_focus_areas PRIMARY KEY (id),
    CONSTRAINT fk_technical_focus_areas__profile_localization
        FOREIGN KEY (profile_localization_id)
        REFERENCES portfolio.profile_localizations (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_technical_focus_areas__localization_sort_order UNIQUE (profile_localization_id, sort_order),
    CONSTRAINT ck_technical_focus_areas__name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_technical_focus_areas__description_not_blank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT ck_technical_focus_areas__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_technical_focus_areas__profile_localization_id
    ON portfolio.technical_focus_areas (profile_localization_id);

CREATE TABLE portfolio.education_summaries (
    id uuid NOT NULL,
    profile_localization_id uuid NOT NULL,
    institution text NOT NULL,
    program text NOT NULL,
    description text NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_education_summaries PRIMARY KEY (id),
    CONSTRAINT fk_education_summaries__profile_localization
        FOREIGN KEY (profile_localization_id)
        REFERENCES portfolio.profile_localizations (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_education_summaries__localization_sort_order UNIQUE (profile_localization_id, sort_order),
    CONSTRAINT ck_education_summaries__institution_not_blank CHECK (btrim(institution) <> ''),
    CONSTRAINT ck_education_summaries__program_not_blank CHECK (btrim(program) <> ''),
    CONSTRAINT ck_education_summaries__description_not_blank CHECK (description IS NULL OR btrim(description) <> ''),
    CONSTRAINT ck_education_summaries__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_education_summaries__profile_localization_id
    ON portfolio.education_summaries (profile_localization_id);

CREATE TABLE portfolio.current_focus_items (
    id uuid NOT NULL,
    profile_localization_id uuid NOT NULL,
    text text NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_current_focus_items PRIMARY KEY (id),
    CONSTRAINT fk_current_focus_items__profile_localization
        FOREIGN KEY (profile_localization_id)
        REFERENCES portfolio.profile_localizations (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_current_focus_items__localization_sort_order UNIQUE (profile_localization_id, sort_order),
    CONSTRAINT ck_current_focus_items__text_not_blank CHECK (btrim(text) <> ''),
    CONSTRAINT ck_current_focus_items__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_current_focus_items__profile_localization_id
    ON portfolio.current_focus_items (profile_localization_id);

CREATE TABLE portfolio.projects (
    id uuid NOT NULL,
    status text NOT NULL,
    visibility text NOT NULL,
    featured boolean NOT NULL,
    sort_order integer NULL,
    cover_asset_id uuid NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT ck_projects__status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED', 'EXPERIMENT')),
    CONSTRAINT ck_projects__visibility CHECK (visibility IN ('PUBLIC', 'UNLISTED', 'PRIVATE')),
    CONSTRAINT ck_projects__sort_order_positive CHECK (sort_order IS NULL OR sort_order > 0),
    CONSTRAINT ck_projects__featured_public CHECK (featured = false OR visibility = 'PUBLIC'),
    CONSTRAINT ck_projects__featured_not_archived CHECK (featured = false OR status <> 'ARCHIVED'),
    CONSTRAINT ck_projects__updated_not_before_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_projects__version_non_negative CHECK (version >= 0)
);

CREATE INDEX ix_projects__status
    ON portfolio.projects (status);

CREATE INDEX ix_projects__visibility
    ON portfolio.projects (visibility);

CREATE INDEX ix_projects__featured
    ON portfolio.projects (featured)
    WHERE featured = true;

CREATE INDEX ix_projects__sort_order
    ON portfolio.projects (sort_order)
    WHERE sort_order IS NOT NULL;

CREATE TABLE portfolio.project_localizations (
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    language text NOT NULL,
    slug text NOT NULL,
    title text NOT NULL,
    summary text NOT NULL,
    CONSTRAINT pk_project_localizations PRIMARY KEY (id),
    CONSTRAINT fk_project_localizations__project
        FOREIGN KEY (project_id)
        REFERENCES portfolio.projects (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_project_localizations__project_language UNIQUE (project_id, language),
    CONSTRAINT uq_project_localizations__language_slug UNIQUE (language, slug),
    CONSTRAINT ck_project_localizations__language CHECK (language IN ('TR', 'EN')),
    CONSTRAINT ck_project_localizations__slug_not_blank CHECK (btrim(slug) <> ''),
    CONSTRAINT ck_project_localizations__slug_format CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    CONSTRAINT ck_project_localizations__title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_project_localizations__summary_not_blank CHECK (btrim(summary) <> '')
);

CREATE INDEX ix_project_localizations__project_id
    ON portfolio.project_localizations (project_id);

CREATE INDEX ix_project_localizations__language_slug
    ON portfolio.project_localizations (language, slug);

CREATE TABLE portfolio.project_case_study_sections (
    id uuid NOT NULL,
    project_localization_id uuid NOT NULL,
    type text NOT NULL,
    body text NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_project_case_study_sections PRIMARY KEY (id),
    CONSTRAINT fk_project_case_study_sections__project_localization
        FOREIGN KEY (project_localization_id)
        REFERENCES portfolio.project_localizations (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_project_case_study_sections__localization_type UNIQUE (project_localization_id, type),
    CONSTRAINT uq_project_case_study_sections__localization_sort_order UNIQUE (project_localization_id, sort_order),
    CONSTRAINT ck_project_case_study_sections__type CHECK (type IN ('PROBLEM', 'CONTEXT', 'ROLE', 'APPROACH', 'ARCHITECTURE', 'DECISIONS', 'TRADEOFFS', 'RESULT', 'LESSONS', 'FUTURE')),
    CONSTRAINT ck_project_case_study_sections__body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_project_case_study_sections__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_project_case_study_sections__project_localization_id
    ON portfolio.project_case_study_sections (project_localization_id);

CREATE TABLE portfolio.project_technologies (
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    name text NOT NULL,
    normalized_name text NOT NULL,
    category text NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_project_technologies PRIMARY KEY (id),
    CONSTRAINT fk_project_technologies__project
        FOREIGN KEY (project_id)
        REFERENCES portfolio.projects (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_project_technologies__project_normalized_category UNIQUE (project_id, normalized_name, category),
    CONSTRAINT uq_project_technologies__project_sort_order UNIQUE (project_id, sort_order),
    CONSTRAINT ck_project_technologies__name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_project_technologies__normalized_name_not_blank CHECK (btrim(normalized_name) <> ''),
    CONSTRAINT ck_project_technologies__category CHECK (category IN ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'INFRA', 'TOOL', 'LIBRARY')),
    CONSTRAINT ck_project_technologies__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_project_technologies__project_id
    ON portfolio.project_technologies (project_id);

CREATE INDEX ix_project_technologies__category
    ON portfolio.project_technologies (category);

CREATE TABLE portfolio.project_links (
    id uuid NOT NULL,
    project_id uuid NOT NULL,
    label text NOT NULL,
    url text NOT NULL,
    link_type text NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT pk_project_links PRIMARY KEY (id),
    CONSTRAINT fk_project_links__project
        FOREIGN KEY (project_id)
        REFERENCES portfolio.projects (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_project_links__project_sort_order UNIQUE (project_id, sort_order),
    CONSTRAINT ck_project_links__label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT ck_project_links__url_not_blank CHECK (btrim(url) <> ''),
    CONSTRAINT ck_project_links__link_type CHECK (link_type IN ('SOURCE', 'DEMO', 'DOCUMENTATION', 'PAPER', 'OTHER')),
    CONSTRAINT ck_project_links__sort_order_positive CHECK (sort_order > 0)
);

CREATE INDEX ix_project_links__project_id
    ON portfolio.project_links (project_id);

CREATE TABLE portfolio.project_tags (
    project_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    assigned_at timestamp with time zone NOT NULL,
    CONSTRAINT pk_project_tags PRIMARY KEY (project_id, tag_id),
    CONSTRAINT fk_project_tags__project
        FOREIGN KEY (project_id)
        REFERENCES portfolio.projects (id)
        ON DELETE CASCADE
);

CREATE INDEX ix_project_tags__project_id
    ON portfolio.project_tags (project_id);

CREATE INDEX ix_project_tags__tag_id
    ON portfolio.project_tags (tag_id);
